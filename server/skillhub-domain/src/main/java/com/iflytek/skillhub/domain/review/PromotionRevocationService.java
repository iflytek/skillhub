package com.iflytek.skillhub.domain.review;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.service.SkillHardDeleteService;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reviews a request to remove a specific derived global skill, preserving its promotion history. */
@Service
public class PromotionRevocationService {
    private final PromotionRequestRepository promotionRepository;
    private final PromotionRevocationRequestRepository revocationRepository;
    private final PromotionRevocationHistoryRepository historyRepository;
    private final SkillRepository skillRepository;
    private final NamespaceRepository namespaceRepository;
    private final SkillHardDeleteService hardDeleteService;
    private final Clock clock;

    public PromotionRevocationService(PromotionRequestRepository promotionRepository,
                                      PromotionRevocationRequestRepository revocationRepository,
                                      PromotionRevocationHistoryRepository historyRepository,
                                      SkillRepository skillRepository,
                                      NamespaceRepository namespaceRepository,
                                      SkillHardDeleteService hardDeleteService,
                                      Clock clock) {
        this.promotionRepository = promotionRepository;
        this.revocationRepository = revocationRepository;
        this.historyRepository = historyRepository;
        this.skillRepository = skillRepository;
        this.namespaceRepository = namespaceRepository;
        this.hardDeleteService = hardDeleteService;
        this.clock = clock;
    }

    @Transactional
    public PromotionRevocationRequest submit(Long sourceSkillId, String actorId,
                                             Map<Long, NamespaceRole> namespaceRoles,
                                             Set<String> platformRoles, String reason) {
        Skill source = skillRepository.findById(sourceSkillId)
                .orElseThrow(() -> new DomainNotFoundException("skill.not_found", sourceSkillId));
        if (!canSubmit(source, actorId, namespaceRoles, platformRoles)) {
            throw new DomainForbiddenException("promotion.revocation.submit_no_permission");
        }
        PromotionRequest initial = promotionRepository.findActiveInitialBySourceSkillId(sourceSkillId)
                .orElseThrow(() -> new DomainBadRequestException("promotion.revocation.not_active"));
        Skill target = validatedTarget(initial, source);
        if (revocationRepository.existsByTargetSkillIdAndStatus(target.getId(), ReviewTaskStatus.PENDING)) {
            throw new DomainBadRequestException("promotion.revocation.duplicate_pending");
        }
        PromotionRevocationRequest request = new PromotionRevocationRequest(
                initial.getId(), source.getId(), target.getId(), source.getNamespaceId(),
                target.getNamespaceId(), target.getSlug(), actorId, reason);
        try {
            return revocationRepository.save(request);
        } catch (DataIntegrityViolationException ex) {
            DomainBadRequestException conflict = new DomainBadRequestException("promotion.revocation.duplicate_pending");
            conflict.initCause(ex);
            throw conflict;
        }
    }

    @Transactional
    public PromotionRevocationRequest approve(Long requestId, String reviewerId,
                                               Set<String> platformRoles,
                                               String comment, String clientIp, String userAgent) {
        PromotionRevocationRequest request = pendingRequest(requestId);
        assertReviewer(request, reviewerId, platformRoles, false);
        executeApproval(request, reviewerId, comment, clientIp, userAgent);
        return request;
    }

    @Transactional
    public PromotionRevocationRequest reject(Long requestId, String reviewerId,
                                              Set<String> platformRoles, String comment) {
        PromotionRevocationRequest request = pendingRequest(requestId);
        assertReviewer(request, reviewerId, platformRoles, false);
        request.review(ReviewTaskStatus.REJECTED, reviewerId, comment, Instant.now(clock));
        return revocationRepository.save(request);
    }

    @Transactional
    public PromotionRevocationRequest revokeDirect(Long sourceSkillId, String administratorId,
                                                    Set<String> platformRoles, String reason,
                                                    String clientIp, String userAgent) {
        if (!isPlatformAdmin(platformRoles)) {
            throw new DomainForbiddenException("promotion.revocation.review_no_permission");
        }
        Skill source = skillRepository.findById(sourceSkillId)
                .orElseThrow(() -> new DomainNotFoundException("skill.not_found", sourceSkillId));
        PromotionRequest initial = promotionRepository.findActiveInitialBySourceSkillId(sourceSkillId)
                .orElseThrow(() -> new DomainBadRequestException("promotion.revocation.not_active"));
        Skill target = validatedTarget(initial, source);
        var pending = revocationRepository.findByTargetSkillIdAndStatus(target.getId(), ReviewTaskStatus.PENDING);
        if (pending.isPresent()) {
            PromotionRevocationRequest request = pending.get();
            executeApproval(request, administratorId, reason, clientIp, userAgent);
            return request;
        }
        PromotionRevocationRequest request = submit(sourceSkillId, administratorId,
                Map.of(), platformRoles, reason);
        executeApproval(request, administratorId, reason, clientIp, userAgent);
        return request;
    }

    private void executeApproval(PromotionRevocationRequest request, String reviewerId,
                                 String comment, String clientIp, String userAgent) {
        Long targetId = request.getTargetSkillId();
        if (!historyRepository.lockTarget(targetId)) {
            throw new DomainBadRequestException("promotion.revocation.not_active");
        }
        Skill source = skillRepository.findById(request.getSourceSkillId())
                .orElseThrow(() -> new DomainBadRequestException("promotion.revocation.not_active"));
        PromotionRequest initial = promotionRepository.findActiveInitialBySourceSkillId(source.getId())
                .orElseThrow(() -> new DomainBadRequestException("promotion.revocation.not_active"));
        if (!initial.getId().equals(request.getInitialPromotionRequestId())
                || !targetId.equals(initial.getTargetSkillId())) {
            throw new DomainBadRequestException("promotion.revocation.target_changed");
        }
        Skill target = validatedTarget(initial, source);
        if (!request.getTargetNamespaceId().equals(target.getNamespaceId())
                || !request.getSkillSlug().equals(target.getSlug())) {
            throw new DomainBadRequestException("promotion.revocation.target_changed");
        }
        Namespace namespace = namespaceRepository.findById(target.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", target.getNamespaceId()));
        if (namespace.getType() != NamespaceType.GLOBAL) {
            throw new DomainBadRequestException("promotion.revocation.target_changed");
        }

        Instant now = Instant.now(clock);
        int detached = historyRepository.detachTarget(targetId, reviewerId, now);
        if (detached < 1) {
            throw new DomainBadRequestException("promotion.revocation.target_changed");
        }
        hardDeleteService.hardDeleteRevokedPromotionTarget(target, namespace.getSlug(),
                reviewerId, clientIp, userAgent);
        request.review(ReviewTaskStatus.APPROVED, reviewerId, comment, now);
        revocationRepository.save(request);
    }

    private Skill validatedTarget(PromotionRequest initial, Skill source) {
        Long targetId = initial.getTargetSkillId();
        if (targetId == null) {
            throw new DomainBadRequestException("promotion.revocation.not_active");
        }
        Skill target = skillRepository.findById(targetId)
                .orElseThrow(() -> new DomainBadRequestException("promotion.revocation.not_active"));
        if (!source.getId().equals(target.getSourceSkillId())
                || !initial.getTargetNamespaceId().equals(target.getNamespaceId())
                || !source.getOwnerId().equals(target.getOwnerId())
                || !source.getSlug().equals(target.getSlug())) {
            throw new DomainBadRequestException("promotion.revocation.target_changed");
        }
        return target;
    }

    private PromotionRevocationRequest pendingRequest(Long id) {
        PromotionRevocationRequest request = revocationRepository.findById(id)
                .orElseThrow(() -> new DomainNotFoundException("promotion.revocation.not_found", id));
        if (request.getStatus() != ReviewTaskStatus.PENDING) {
            throw new DomainBadRequestException("promotion.revocation.not_pending", id);
        }
        return request;
    }

    private boolean canSubmit(Skill source, String actorId, Map<Long, NamespaceRole> namespaceRoles,
                              Set<String> platformRoles) {
        NamespaceRole role = namespaceRoles.get(source.getNamespaceId());
        return actorId.equals(source.getOwnerId()) || role == NamespaceRole.OWNER
                || role == NamespaceRole.ADMIN || isPlatformAdmin(platformRoles);
    }

    private void assertReviewer(PromotionRevocationRequest request, String reviewerId,
                                Set<String> platformRoles, boolean direct) {
        if (!isPlatformAdmin(platformRoles) ||
                (!direct && request.getSubmittedBy().equals(reviewerId)
                        && !platformRoles.contains("SUPER_ADMIN"))) {
            throw new DomainForbiddenException("promotion.revocation.review_no_permission");
        }
    }

    private boolean isPlatformAdmin(Set<String> roles) {
        return roles.contains("SKILL_ADMIN") || roles.contains("SUPER_ADMIN");
    }
}
