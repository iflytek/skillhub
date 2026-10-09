package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.audit.AuditDetail;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.review.PromotionRevocationRequest;
import com.iflytek.skillhub.domain.review.PromotionRevocationRequestRepository;
import com.iflytek.skillhub.domain.review.PromotionRevocationService;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.dto.PromotionRevocationResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.search.SearchIndexService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

/** API-facing revocation workflow, including transactionally removing the search document. */
@Service
public class PromotionRevocationPortalAppService {
    private final PromotionRevocationService revocationService;
    private final PromotionRevocationRequestRepository revocationRepository;
    private final PromotionRequestRepository promotionRepository;
    private final SkillRepository skillRepository;
    private final RbacService rbacService;
    private final SearchIndexService searchIndexService;
    private final AuditLogService auditLogService;
    private final RequestIdAccessor requestIdAccessor;

    public PromotionRevocationPortalAppService(PromotionRevocationService revocationService,
                                               PromotionRevocationRequestRepository revocationRepository,
                                               PromotionRequestRepository promotionRepository,
                                               SkillRepository skillRepository, RbacService rbacService,
                                               SearchIndexService searchIndexService,
                                               AuditLogService auditLogService,
                                               RequestIdAccessor requestIdAccessor) {
        this.revocationService = revocationService;
        this.revocationRepository = revocationRepository;
        this.promotionRepository = promotionRepository;
        this.skillRepository = skillRepository;
        this.rbacService = rbacService;
        this.searchIndexService = searchIndexService;
        this.auditLogService = auditLogService;
        this.requestIdAccessor = requestIdAccessor;
    }

    @Transactional
    public PromotionRevocationResponse submit(Long sourceSkillId, String userId,
                                              Map<Long, NamespaceRole> namespaceRoles,
                                              String reason, AuditRequestContext context) {
        PromotionRevocationRequest request = revocationService.submit(sourceSkillId, userId,
                roles(namespaceRoles), platformRoles(userId), reason);
        audit("PROMOTION_REVOCATION_SUBMIT", request, userId, context);
        return PromotionRevocationResponse.from(request);
    }

    @Transactional
    public PromotionRevocationResponse approve(Long requestId, String userId,
                                               String comment, AuditRequestContext context) {
        revocationRepository.findById(requestId)
                .ifPresent(pending -> searchIndexService.remove(pending.getTargetSkillId()));
        PromotionRevocationRequest request = revocationService.approve(requestId, userId,
                platformRoles(userId), comment, clientIp(context), userAgent(context));
        audit("PROMOTION_REVOCATION_APPROVE", request, userId, context);
        return PromotionRevocationResponse.from(request);
    }

    @Transactional
    public PromotionRevocationResponse reject(Long requestId, String userId,
                                              String comment, AuditRequestContext context) {
        PromotionRevocationRequest request = revocationService.reject(requestId, userId,
                platformRoles(userId), comment);
        audit("PROMOTION_REVOCATION_REJECT", request, userId, context);
        return PromotionRevocationResponse.from(request);
    }

    @Transactional
    public PromotionRevocationResponse revokeDirect(Long sourceSkillId, String userId,
                                                    String reason, AuditRequestContext context) {
        promotionRepository.findActiveInitialBySourceSkillId(sourceSkillId)
                .ifPresent(initial -> searchIndexService.remove(initial.getTargetSkillId()));
        PromotionRevocationRequest request = revocationService.revokeDirect(sourceSkillId, userId,
                platformRoles(userId), reason, clientIp(context), userAgent(context));
        audit("PROMOTION_REVOCATION_DIRECT", request, userId, context);
        return PromotionRevocationResponse.from(request);
    }

    public PromotionRevocationResponse get(Long requestId, String userId,
                                           Map<Long, NamespaceRole> namespaceRoles) {
        PromotionRevocationRequest request = revocationRepository.findById(requestId)
                .orElseThrow(() -> new DomainNotFoundException("promotion.revocation.not_found", requestId));
        requireRead(request, userId, namespaceRoles);
        return PromotionRevocationResponse.from(request);
    }

    public List<PromotionRevocationResponse> history(Long sourceSkillId, String userId,
                                                     Map<Long, NamespaceRole> namespaceRoles) {
        requireReadSource(sourceSkillId, userId, namespaceRoles);
        return revocationRepository.findBySourceSkillIdOrderBySubmittedAtDesc(sourceSkillId).stream()
                .map(PromotionRevocationResponse::from).toList();
    }

    public List<PromotionRevocationResponse> pending(String userId) {
        requirePlatformAdmin(userId);
        return revocationRepository.findByStatusOrderBySubmittedAtAsc(ReviewTaskStatus.PENDING).stream()
                .map(PromotionRevocationResponse::from).toList();
    }

    public PageResponse<PromotionRevocationResponse> reviewedHistory(String userId, int page, int size) {
        requirePlatformAdmin(userId);
        if (page < 0 || size < 1 || size > 100) {
            throw new DomainBadRequestException("promotion.revocation.page_invalid");
        }
        return PageResponse.from(revocationRepository.findByStatusIn(
                List.of(ReviewTaskStatus.APPROVED, ReviewTaskStatus.REJECTED),
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("reviewedAt"), Sort.Order.desc("id")))
        ).map(PromotionRevocationResponse::from));
    }

    private void requireRead(PromotionRevocationRequest request, String userId,
                             Map<Long, NamespaceRole> namespaceRoles) {
        if (request.getSubmittedBy().equals(userId) || isPlatformAdmin(platformRoles(userId))) {
            return;
        }
        NamespaceRole role = roles(namespaceRoles).get(request.getSourceNamespaceId());
        if (role != NamespaceRole.OWNER && role != NamespaceRole.ADMIN) {
            throw new DomainForbiddenException("promotion.revocation.read_no_permission");
        }
    }

    private void requireReadSource(Long sourceSkillId, String userId,
                                   Map<Long, NamespaceRole> namespaceRoles) {
        if (isPlatformAdmin(platformRoles(userId))) {
            return;
        }
        Skill skill = skillRepository.findById(sourceSkillId)
                .orElseThrow(() -> new DomainNotFoundException("skill.not_found", sourceSkillId));
        NamespaceRole role = roles(namespaceRoles).get(skill.getNamespaceId());
        if (!userId.equals(skill.getOwnerId()) && role != NamespaceRole.OWNER && role != NamespaceRole.ADMIN) {
            throw new DomainForbiddenException("promotion.revocation.read_no_permission");
        }
    }

    private void requirePlatformAdmin(String userId) {
        if (!isPlatformAdmin(platformRoles(userId))) {
            throw new DomainForbiddenException("promotion.revocation.review_no_permission");
        }
    }

    private boolean isPlatformAdmin(Set<String> roles) {
        return roles.contains("SKILL_ADMIN") || roles.contains("SUPER_ADMIN");
    }

    private Set<String> platformRoles(String userId) { return rbacService.getUserRoleCodes(userId); }
    private Map<Long, NamespaceRole> roles(Map<Long, NamespaceRole> roles) {
        return roles != null ? roles : Map.of();
    }
    private String clientIp(AuditRequestContext context) { return context != null ? context.clientIp() : null; }
    private String userAgent(AuditRequestContext context) { return context != null ? context.userAgent() : null; }

    private void audit(String action, PromotionRevocationRequest request, String actorId,
                       AuditRequestContext context) {
        auditLogService.record(actorId, action, "PROMOTION_REVOCATION_REQUEST", request.getId(),
                requestIdAccessor.current(), clientIp(context), userAgent(context),
                AuditDetail.builder().put("sourceSkillId", request.getSourceSkillId())
                        .put("targetSkillId", request.getTargetSkillId())
                        .put("status", request.getStatus().name()).build());
    }
}
