package com.iflytek.skillhub.domain.review;

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
import org.springframework.context.ApplicationEventPublisher;
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
    private final Clock clock;

    public PromotionService(PromotionRequestRepository promotionRequestRepository,
                            SkillRepository skillRepository,
                            SkillVersionRepository skillVersionRepository,
                            SkillFileRepository skillFileRepository,
                            NamespaceRepository namespaceRepository,
                            ReviewPermissionChecker permissionChecker,
                            ApplicationEventPublisher eventPublisher,
                            GovernanceNotificationService governanceNotificationService,
                            Clock clock) {
        this.promotionRequestRepository = promotionRequestRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillFileRepository = skillFileRepository;
        this.namespaceRepository = namespaceRepository;
        this.permissionChecker = permissionChecker;
        this.eventPublisher = eventPublisher;
        this.governanceNotificationService = governanceNotificationService;
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

        Namespace sourceNamespace = namespaceRepository.findById(sourceSkill.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", sourceSkill.getNamespaceId()));
        assertNamespaceActive(sourceNamespace);

        if (!permissionChecker.canSubmitPromotion(sourceSkill, userId, userNamespaceRoles, platformRoles)) {
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
        promotionRequestRepository.findBySourceSkillIdAndStatus(sourceSkillId, ReviewTaskStatus.APPROVED)
                .ifPresent(existing -> {
                    throw new DomainBadRequestException("promotion.already_promoted", sourceSkillId);
                });

        PromotionRequest request = new PromotionRequest(sourceSkillId, sourceVersionId, targetNamespaceId, userId);
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

        Namespace sourceNamespace = namespaceRepository.findById(sourceSkill.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", sourceSkill.getNamespaceId()));
        assertNamespaceActive(sourceNamespace);

        if (!permissionChecker.canSubmitPromotion(sourceSkill, userId, userNamespaceRoles)) {
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
        promotionRequestRepository.findBySourceSkillIdAndStatus(sourceSkillId, ReviewTaskStatus.APPROVED)
                .ifPresent(existing -> {
                    throw new DomainBadRequestException("promotion.already_promoted", sourceSkillId);
                });

        PromotionRequest request = new PromotionRequest(sourceSkillId, sourceVersionId, targetNamespaceId, userId);
        PromotionRequest saved = promotionRequestRepository.save(request);
        eventPublisher.publishEvent(new PromotionSubmittedEvent(
                saved.getId(), saved.getSourceSkillId(), saved.getSourceVersionId(),
                saved.getSubmittedBy()));
        return saved;
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
                promotionId, ReviewTaskStatus.APPROVED, reviewerId, comment, null, request.getVersion());
        if (updated == 0) {
            throw new ConcurrentModificationException("Promotion request was modified concurrently");
        }

        PromotionRequest approvedRequest = promotionRequestRepository.findById(promotionId)
                .orElseThrow(() -> new DomainNotFoundException("promotion.not_found", promotionId));

        Skill sourceSkill = skillRepository.findById(approvedRequest.getSourceSkillId())
                .orElseThrow(() -> new DomainNotFoundException("skill.not_found", approvedRequest.getSourceSkillId()));

        SkillVersion sourceVersion = skillVersionRepository.findById(approvedRequest.getSourceVersionId())
                .orElseThrow(() -> new DomainNotFoundException("skill_version.not_found", approvedRequest.getSourceVersionId()));

        // Create new skill in global namespace
        Skill newSkill = new Skill(approvedRequest.getTargetNamespaceId(), sourceSkill.getSlug(),
                sourceSkill.getOwnerId(), SkillVisibility.PUBLIC);
        newSkill.setDisplayName(sourceSkill.getDisplayName());
        newSkill.setSummary(sourceSkill.getSummary());
        newSkill.setSourceSkillId(sourceSkill.getId());
        newSkill.setCreatedBy(reviewerId);
        newSkill.setUpdatedBy(reviewerId);
        newSkill = skillRepository.save(newSkill);

        // Create new version copying metadata from source
        SkillVersion newVersion = new SkillVersion(newSkill.getId(), sourceVersion.getVersion(),
                sourceVersion.getCreatedBy());
        newVersion.setStatus(SkillVersionStatus.PUBLISHED);
        newVersion.setPublishedAt(currentTime());
        newVersion.setRequestedVisibility(SkillVisibility.PUBLIC);
        newVersion.setChangelog(sourceVersion.getChangelog());
        newVersion.setParsedMetadataJson(sourceVersion.getParsedMetadataJson());
        newVersion.setManifestJson(sourceVersion.getManifestJson());
        newVersion.setFileCount(sourceVersion.getFileCount());
        newVersion.setTotalSize(sourceVersion.getTotalSize());
        newVersion = skillVersionRepository.save(newVersion);

        // Update skill's latest version
        newSkill.setLatestVersionId(newVersion.getId());
        skillRepository.save(newSkill);

        // Copy file records (reuse storageKey)
        List<SkillFile> sourceFiles = skillFileRepository.findByVersionId(approvedRequest.getSourceVersionId());
        Long newVersionId = newVersion.getId();
        List<SkillFile> copiedFiles = sourceFiles.stream()
                .map(f -> new SkillFile(newVersionId, f.getFilePath(), f.getFileSize(),
                        f.getContentType(), f.getSha256(), f.getStorageKey()))
                .toList();
        skillFileRepository.saveAll(copiedFiles);

        // Update promotion request with target skill id
        approvedRequest.setTargetSkillId(newSkill.getId());
        PromotionRequest savedRequest = promotionRequestRepository.save(approvedRequest);

        eventPublisher.publishEvent(new SkillPublishedEvent(
                newSkill.getId(), newVersion.getId(), reviewerId));
        eventPublisher.publishEvent(new PromotionApprovedEvent(
                approvedRequest.getId(), approvedRequest.getSourceSkillId(),
                reviewerId, approvedRequest.getSubmittedBy()));
        governanceNotificationService.notifyUser(
                approvedRequest.getSubmittedBy(),
                "PROMOTION",
                "PROMOTION_REQUEST",
                promotionId,
                "Promotion approved",
                "{\"status\":\"APPROVED\"}"
        );

        return savedRequest;
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
                promotionId, ReviewTaskStatus.REJECTED, reviewerId, comment, null, request.getVersion());
        if (updated == 0) {
            throw new ConcurrentModificationException("Promotion request was modified concurrently");
        }
        eventPublisher.publishEvent(new PromotionRejectedEvent(
                request.getId(), request.getSourceSkillId(),
                reviewerId, request.getSubmittedBy(), comment));
        governanceNotificationService.notifyUser(
                request.getSubmittedBy(),
                "PROMOTION",
                "PROMOTION_REQUEST",
                promotionId,
                "Promotion rejected",
                "{\"status\":\"REJECTED\"}"
        );

        return promotionRequestRepository.findById(promotionId).orElse(request);
    }

    public boolean canViewPromotion(PromotionRequest request, String userId, Set<String> platformRoles) {
        return permissionChecker.canViewPromotion(request, userId, platformRoles);
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
}
