package com.iflytek.skillhub.domain.review;

import com.iflytek.skillhub.domain.audit.AuditDetail;
import com.iflytek.skillhub.domain.event.PromotionApprovedEvent;
import com.iflytek.skillhub.domain.event.PromotionRejectedEvent;
import com.iflytek.skillhub.domain.event.PromotionSubmittedEvent;
import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.governance.GovernanceNotificationService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.*;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles promotion requests that copy approved skills into the global
 * namespace.
 *
 * <p>Promotion is intentionally modeled separately from normal review because
 * it creates or updates a distinct target skill lineage.
 */
@Service
public class PromotionService {

    private final PromotionRequestRepository promotionRequestRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillFileRepository skillFileRepository;
    private final NamespaceRepository namespaceRepository;
    private final ReviewPermissionChecker permissionChecker;
    private final ApplicationEventPublisher eventPublisher;
    private final GovernanceNotificationService governanceNotificationService;
    private final EntityManager entityManager;
    private final Clock clock;

    public PromotionService(PromotionRequestRepository promotionRequestRepository,
                            SkillRepository skillRepository,
                            SkillVersionRepository skillVersionRepository,
                            SkillFileRepository skillFileRepository,
                            NamespaceRepository namespaceRepository,
                            ReviewPermissionChecker permissionChecker,
                            ApplicationEventPublisher eventPublisher,
                            GovernanceNotificationService governanceNotificationService,
                            EntityManager entityManager,
                            Clock clock) {
        this.promotionRequestRepository = promotionRequestRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillFileRepository = skillFileRepository;
        this.namespaceRepository = namespaceRepository;
        this.permissionChecker = permissionChecker;
        this.eventPublisher = eventPublisher;
        this.governanceNotificationService = governanceNotificationService;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /**
     * Submits a promotion request for a published source version using both
     * namespace and platform roles for authorization.
     */
    @Transactional
    public PromotionRequest submitPromotion(Long sourceSkillId, Long sourceVersionId,
                                            Long targetNamespaceId, String userId,
                                            Map<Long, NamespaceRole> userNamespaceRoles,
                                            Set<String> platformRoles) {
        return submitPromotionInternal(sourceSkillId, sourceVersionId, targetNamespaceId,
                userId, userNamespaceRoles, platformRoles, false);
    }

    private PromotionRequest submitPromotionInternal(Long sourceSkillId, Long sourceVersionId,
                                            Long targetNamespaceId, String userId,
                                            Map<Long, NamespaceRole> userNamespaceRoles,
                                            Set<String> platformRoles, boolean legacyAuth) {
        Skill sourceSkill = skillRepository.findById(sourceSkillId)
                .orElseThrow(() -> new DomainNotFoundException("skill.not_found", sourceSkillId));

        SkillVersion sourceVersion = skillVersionRepository.findById(sourceVersionId)
                .orElseThrow(() -> new DomainNotFoundException("skill_version.not_found", sourceVersionId));

        if (!sourceVersion.getSkillId().equals(sourceSkillId)) {
            throw new DomainBadRequestException("promotion.version_skill_mismatch", sourceVersionId, sourceSkillId);
        }

        if (sourceVersion.getStatus() != SkillVersionStatus.PUBLISHED) {
            throw new DomainBadRequestException("promotion.version_not_published", sourceVersionId);
        }

        assertSkillActive(sourceSkill);

        Namespace sourceNamespace = namespaceRepository.findById(sourceSkill.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", sourceSkill.getNamespaceId()));
        assertNamespaceActive(sourceNamespace);

        boolean permitted = legacyAuth
                ? permissionChecker.canSubmitPromotion(sourceSkill, userId, userNamespaceRoles)
                : permissionChecker.canSubmitPromotion(sourceSkill, userId, userNamespaceRoles, platformRoles);
        if (!permitted) {
            throw new DomainForbiddenException("promotion.submit.no_permission");
        }

        Namespace targetNamespace = namespaceRepository.findById(targetNamespaceId)
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", targetNamespaceId));

        if (targetNamespace.getType() != NamespaceType.GLOBAL) {
            throw new DomainBadRequestException("promotion.target_not_global", targetNamespaceId);
        }

        promotionRequestRepository.findBySourceSkillIdAndStatus(sourceSkillId, ReviewTaskStatus.PENDING)
                .ifPresent(existing -> {
                    throw new DomainBadRequestException("promotion.duplicate_pending", sourceVersionId);
                });
        PromotionRequest request = new PromotionRequest(sourceSkillId, sourceVersionId, targetNamespaceId, userId);
        promotionRequestRepository.findActiveInitialBySourceSkillId(sourceSkillId)
                .ifPresent(initial -> {
                    Skill target = requireActiveTarget(initial, sourceSkill, targetNamespaceId);
                    assertTargetVersionAvailable(target.getId(), sourceVersion.getVersion());
                    request.setRequestKind(PromotionRequestKind.UPDATE);
                    request.setTargetSkillId(target.getId());
                });
        PromotionRequest saved = promotionRequestRepository.save(request);
        eventPublisher.publishEvent(new PromotionSubmittedEvent(
                saved.getId(), saved.getSourceSkillId(), saved.getSourceVersionId(),
                saved.getSubmittedBy()));
        return saved;
    }

    @Transactional
    public PromotionRequest submitPromotion(Long sourceSkillId, Long sourceVersionId,
                                            Long targetNamespaceId, String userId,
                                            Map<Long, NamespaceRole> userNamespaceRoles) {
        return submitPromotionInternal(sourceSkillId, sourceVersionId, targetNamespaceId, userId,
                userNamespaceRoles, Set.of(), true);
    }

    /**
     * Approves a promotion request and materializes a published copy of the
     * source version in the target global namespace.
     */
    @Transactional
    public PromotionRequest approvePromotion(Long promotionId, String reviewerId,
                                             String comment, Set<String> platformRoles) {
        PromotionRequest request = promotionRequestRepository.findById(promotionId)
                .orElseThrow(() -> new DomainNotFoundException("promotion.not_found", promotionId));

        if (request.getStatus() != ReviewTaskStatus.PENDING) {
            throw new DomainBadRequestException("promotion.not_pending", promotionId);
        }

        if (!permissionChecker.canReviewPromotion(request, reviewerId, platformRoles)) {
            throw new DomainForbiddenException("promotion.no_permission");
        }

        int updated = promotionRequestRepository.updateStatusWithVersion(
                promotionId, ReviewTaskStatus.APPROVED, reviewerId, comment,
                request.getTargetSkillId(), request.getVersion());
        if (updated == 0) {
            throw new ConcurrentModificationException("Promotion request was modified concurrently");
        }
        PromotionRequest approvedRequest = promotionRequestRepository.findById(promotionId)
                .orElseThrow(() -> new DomainNotFoundException("promotion.not_found", promotionId));

        Skill sourceSkill = skillRepository.findById(approvedRequest.getSourceSkillId())
                .orElseThrow(() -> new DomainNotFoundException("skill.not_found", approvedRequest.getSourceSkillId()));

        SkillVersion sourceVersion = skillVersionRepository.findById(approvedRequest.getSourceVersionId())
                .orElseThrow(() -> new DomainNotFoundException("skill_version.not_found", approvedRequest.getSourceVersionId()));

        if (!sourceVersion.getSkillId().equals(sourceSkill.getId())
                || sourceVersion.getStatus() != SkillVersionStatus.PUBLISHED) {
            throw new DomainBadRequestException("promotion.version_not_published", sourceVersion.getId());
        }
        assertSkillActive(sourceSkill);
        Namespace sourceNamespace = namespaceRepository.findById(sourceSkill.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", sourceSkill.getNamespaceId()));
        assertNamespaceActive(sourceNamespace);

        Skill targetSkill;
        if (approvedRequest.getRequestKind() == PromotionRequestKind.UPDATE) {
            PromotionRequest initial = promotionRequestRepository.findActiveInitialBySourceSkillId(sourceSkill.getId())
                    .orElseThrow(() -> new DomainBadRequestException("promotion.target_inactive", sourceSkill.getId()));
            targetSkill = requireActiveTarget(initial, sourceSkill, approvedRequest.getTargetNamespaceId());
            if (!targetSkill.getId().equals(approvedRequest.getTargetSkillId())) {
                throw new DomainBadRequestException("promotion.target_inactive", sourceSkill.getId());
            }
            // Serialize approval with ordinary uploads that lock target Skill before publishing.
            entityManager.lock(targetSkill, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            assertTargetVersionAvailable(targetSkill.getId(), sourceVersion.getVersion());
            targetSkill.setDisplayName(sourceSkill.getDisplayName());
            targetSkill.setSummary(sourceSkill.getSummary());
        } else {
            if (promotionRequestRepository.findActiveInitialBySourceSkillId(sourceSkill.getId()).isPresent()) {
                throw new DomainBadRequestException("promotion.already_promoted", sourceSkill.getId());
            }
            assertTargetSkillNotExists(approvedRequest, sourceSkill);
            targetSkill = new Skill(approvedRequest.getTargetNamespaceId(), sourceSkill.getSlug(),
                    sourceSkill.getOwnerId(), SkillVisibility.PUBLIC);
            targetSkill.setDisplayName(sourceSkill.getDisplayName());
            targetSkill.setSummary(sourceSkill.getSummary());
            targetSkill.setSourceSkillId(sourceSkill.getId());
            targetSkill.setCreatedBy(reviewerId);
            targetSkill.setUpdatedBy(reviewerId);
            try {
                targetSkill = skillRepository.save(targetSkill);
            } catch (DataIntegrityViolationException ex) {
                throw duplicateTargetSkillConflict(sourceSkill.getSlug(), ex);
            }
        }

        // Create new version copying metadata from source
        SkillVersion newVersion = new SkillVersion(targetSkill.getId(), sourceVersion.getVersion(),
                sourceVersion.getCreatedBy());
        newVersion.setStatus(SkillVersionStatus.PUBLISHED);
        newVersion.setPublishedAt(currentTime());
        newVersion.setRequestedVisibility(SkillVisibility.PUBLIC);
        newVersion.setChangelog(sourceVersion.getChangelog());
        newVersion.setParsedMetadataJson(sourceVersion.getParsedMetadataJson());
        newVersion.setManifestJson(sourceVersion.getManifestJson());
        newVersion.setFileCount(sourceVersion.getFileCount());
        newVersion.setTotalSize(sourceVersion.getTotalSize());
        newVersion.setBundleReady(sourceVersion.isBundleReady());
        newVersion.setDownloadReady(sourceVersion.isDownloadReady());
        try {
            newVersion = skillVersionRepository.save(newVersion);
            skillVersionRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new DomainBadRequestException("promotion.target_version_conflict", sourceVersion.getVersion());
        }

        // Update skill's latest version
        targetSkill.setLatestVersionId(newVersion.getId());
        skillRepository.save(targetSkill);

        // Copy file records (reuse storageKey)
        List<SkillFile> sourceFiles = skillFileRepository.findByVersionId(approvedRequest.getSourceVersionId());
        Long newVersionId = newVersion.getId();
        List<SkillFile> copiedFiles = sourceFiles.stream()
                .map(f -> new SkillFile(newVersionId, f.getFilePath(), f.getFileSize(),
                        f.getContentType(), f.getSha256(), f.getStorageKey()))
                .toList();
        skillFileRepository.saveAll(copiedFiles);

        // Update promotion request with target skill id
        approvedRequest.setTargetSkillId(targetSkill.getId());
        approvedRequest.setTargetVersionId(newVersion.getId());
        PromotionRequest savedRequest = promotionRequestRepository.save(approvedRequest);

        eventPublisher.publishEvent(new SkillPublishedEvent(
                targetSkill.getId(), newVersion.getId(), reviewerId));
        eventPublisher.publishEvent(new PromotionApprovedEvent(
                approvedRequest.getId(), approvedRequest.getSourceSkillId(),
                reviewerId, approvedRequest.getSubmittedBy()));
        governanceNotificationService.notifyUser(
                approvedRequest.getSubmittedBy(),
                "PROMOTION",
                "PROMOTION_REQUEST",
                promotionId,
                "Promotion approved",
                AuditDetail.of("status", "APPROVED")
        );

        return savedRequest;
    }

    private void assertTargetSkillNotExists(PromotionRequest approvedRequest, Skill sourceSkill) {
        for (Skill existing : skillRepository.findByNamespaceIdAndSlug(
                approvedRequest.getTargetNamespaceId(), sourceSkill.getSlug())) {
            if (!skillVersionRepository.findBySkillIdAndStatus(
                            existing.getId(), SkillVersionStatus.PUBLISHED).isEmpty()) {
                throw duplicateTargetSkillConflict(sourceSkill.getSlug(), null);
            }
            if (existing.getOwnerId().equals(sourceSkill.getOwnerId())) {
                throw duplicateTargetSkillConflict(sourceSkill.getSlug(), null);
            }
        }
    }

    private Skill requireActiveTarget(PromotionRequest initial, Skill sourceSkill, Long targetNamespaceId) {
        Skill target = skillRepository.findById(initial.getTargetSkillId())
                .orElseThrow(() -> new DomainBadRequestException("promotion.target_inactive", sourceSkill.getId()));
        if (!target.getNamespaceId().equals(targetNamespaceId)
                || !sourceSkill.getId().equals(target.getSourceSkillId())
                || !sourceSkill.getOwnerId().equals(target.getOwnerId())
                || target.getStatus() != SkillStatus.ACTIVE || target.isHidden()) {
            throw new DomainBadRequestException("promotion.target_inactive", sourceSkill.getId());
        }
        return target;
    }

    private void assertTargetVersionAvailable(Long targetSkillId, String version) {
        if (skillVersionRepository.findBySkillIdAndVersion(targetSkillId, version).isPresent()) {
            throw new DomainBadRequestException("promotion.target_version_conflict", version);
        }
    }

    private void assertSkillActive(Skill skill) {
        if (skill.getStatus() != SkillStatus.ACTIVE || skill.isHidden()) {
            throw new DomainBadRequestException("promotion.source_inactive", skill.getId());
        }
    }

    private DomainBadRequestException duplicateTargetSkillConflict(String slug, Exception cause) {
        DomainBadRequestException ex = new DomainBadRequestException("promotion.target_skill_conflict", slug);
        if (cause != null) {
            ex.initCause(cause);
        }
        return ex;
    }

    /**
     * Rejects a pending promotion request without changing the source skill.
     */
    @Transactional
    public PromotionRequest rejectPromotion(Long promotionId, String reviewerId,
                                            String comment, Set<String> platformRoles) {
        PromotionRequest request = promotionRequestRepository.findById(promotionId)
                .orElseThrow(() -> new DomainNotFoundException("promotion.not_found", promotionId));

        if (request.getStatus() != ReviewTaskStatus.PENDING) {
            throw new DomainBadRequestException("promotion.not_pending", promotionId);
        }

        if (!permissionChecker.canReviewPromotion(request, reviewerId, platformRoles)) {
            throw new DomainForbiddenException("promotion.no_permission");
        }

        int updated = promotionRequestRepository.updateStatusWithVersion(
                promotionId, ReviewTaskStatus.REJECTED, reviewerId, comment,
                request.getTargetSkillId(), request.getVersion());
        if (updated == 0) {
            throw new ConcurrentModificationException("Promotion request was modified concurrently");
        }
        syncPromotionRequestState(request, ReviewTaskStatus.REJECTED, reviewerId, comment);
        entityManager.detach(request);
        eventPublisher.publishEvent(new PromotionRejectedEvent(
                request.getId(), request.getSourceSkillId(),
                reviewerId, request.getSubmittedBy(), comment));
        governanceNotificationService.notifyUser(
                request.getSubmittedBy(),
                "PROMOTION",
                "PROMOTION_REQUEST",
                promotionId,
                "Promotion rejected",
                AuditDetail.of("status", "REJECTED")
        );

        return request;
    }

    public boolean canViewPromotion(PromotionRequest request, String userId, Set<String> platformRoles) {
        return permissionChecker.canViewPromotion(request, userId, platformRoles);
    }

    @Transactional(readOnly = true)
    public PromotionState getSourceState(Long sourceSkillId, String userId,
                                         Map<Long, NamespaceRole> userNamespaceRoles,
                                         Set<String> platformRoles) {
        Skill source = skillRepository.findById(sourceSkillId)
                .orElseThrow(() -> new DomainNotFoundException("skill.not_found", sourceSkillId));
        if (!permissionChecker.canSubmitPromotion(source, userId, userNamespaceRoles, platformRoles)) {
            throw new DomainForbiddenException("promotion.submit.no_permission");
        }
        PromotionRequest pending = promotionRequestRepository
                .findBySourceSkillIdAndStatus(sourceSkillId, ReviewTaskStatus.PENDING).orElse(null);
        PromotionRequest initial = promotionRequestRepository.findActiveInitialBySourceSkillId(sourceSkillId)
                .orElse(null);
        if (initial == null) {
            return new PromotionState(pending != null ? "PENDING" : "INITIAL", null, null,
                    pending != null ? pending.getId() : null);
        }
        Skill target = skillRepository.findById(initial.getTargetSkillId())
                .orElseThrow(() -> new DomainBadRequestException("promotion.target_inactive", sourceSkillId));
        if (!source.getId().equals(target.getSourceSkillId())
                || !source.getOwnerId().equals(target.getOwnerId())
                || !initial.getTargetNamespaceId().equals(target.getNamespaceId())) {
            throw new DomainBadRequestException("promotion.target_inactive", sourceSkillId);
        }
        String currentVersion = target.getLatestVersionId() == null ? null : skillVersionRepository
                .findById(target.getLatestVersionId()).map(SkillVersion::getVersion).orElse(null);
        return new PromotionState(pending != null ? "PENDING" : "UPDATE", target.getId(),
                currentVersion, pending != null ? pending.getId() : null);
    }

    private void assertNamespaceActive(Namespace namespace) {
        if (namespace.getStatus() == NamespaceStatus.FROZEN) {
            throw new DomainBadRequestException("error.namespace.frozen", namespace.getSlug());
        }
        if (namespace.getStatus() == NamespaceStatus.ARCHIVED) {
            throw new DomainBadRequestException("error.namespace.archived", namespace.getSlug());
        }
    }

    private Instant currentTime() {
        return Instant.now(clock);
    }

    private void syncPromotionRequestState(PromotionRequest request,
                                           ReviewTaskStatus status,
                                           String reviewedBy,
                                           String comment) {
        request.setStatus(status);
        request.setReviewedBy(reviewedBy);
        request.setReviewComment(comment);
        request.setReviewedAt(currentTime());
    }
}
