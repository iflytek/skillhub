package com.iflytek.skillhub.domain.review;

import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.*;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Set;

@Service
public class PromotionService {

    private final PromotionRequestRepository promotionRequestRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillFileRepository skillFileRepository;
    private final NamespaceRepository namespaceRepository;
    private final ReviewPermissionChecker permissionChecker;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public PromotionService(PromotionRequestRepository promotionRequestRepository,
                            SkillRepository skillRepository,
                            SkillVersionRepository skillVersionRepository,
                            SkillFileRepository skillFileRepository,
                            NamespaceRepository namespaceRepository,
                            ReviewPermissionChecker permissionChecker,
                            ApplicationEventPublisher eventPublisher,
                            EntityManager entityManager) {
        this.promotionRequestRepository = promotionRequestRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillFileRepository = skillFileRepository;
        this.namespaceRepository = namespaceRepository;
        this.permissionChecker = permissionChecker;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @Transactional
    public PromotionRequest submitPromotion(Long sourceSkillId, Long sourceVersionId,
                                            Long targetNamespaceId, String userId,
                                            java.util.Map<Long, NamespaceRole> userNamespaceRoles,
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

        if (!permissionChecker.canSubmitPromotion(sourceSkill, userId, userNamespaceRoles, platformRoles)) {
            throw new DomainForbiddenException("promotion.submit.no_permission");
        }

        Namespace targetNamespace = namespaceRepository.findById(targetNamespaceId)
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", targetNamespaceId));

        if (targetNamespace.getType() != NamespaceType.GLOBAL) {
            throw new DomainBadRequestException("promotion.target_not_global", targetNamespaceId);
        }

        promotionRequestRepository.findBySourceVersionIdAndStatus(sourceVersionId, ReviewTaskStatus.PENDING)
                .ifPresent(existing -> {
                    throw new DomainBadRequestException("promotion.duplicate_pending", sourceVersionId);
                });

        PromotionRequest request = new PromotionRequest(sourceSkillId, sourceVersionId, targetNamespaceId, userId);
        return promotionRequestRepository.save(request);
    }

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
        entityManager.detach(request);
        request.setStatus(ReviewTaskStatus.APPROVED);
        request.setReviewedBy(reviewerId);
        request.setReviewComment(comment);
        request.setReviewedAt(Instant.now());

        Skill sourceSkill = skillRepository.findById(request.getSourceSkillId())
                .orElseThrow(() -> new DomainNotFoundException("skill.not_found", request.getSourceSkillId()));

        SkillVersion sourceVersion = skillVersionRepository.findById(request.getSourceVersionId())
                .orElseThrow(() -> new DomainNotFoundException("skill_version.not_found", request.getSourceVersionId()));

        // Create new skill in global namespace
        Skill newSkill = new Skill(request.getTargetNamespaceId(), sourceSkill.getSlug(),
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
        newVersion.setPublishedAt(LocalDateTime.now());
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
        List<SkillFile> sourceFiles = skillFileRepository.findByVersionId(request.getSourceVersionId());
        Long newVersionId = newVersion.getId();
        List<SkillFile> copiedFiles = sourceFiles.stream()
                .map(f -> new SkillFile(newVersionId, f.getFilePath(), f.getFileSize(),
                        f.getContentType(), f.getSha256(), f.getStorageKey()))
                .toList();
        skillFileRepository.saveAll(copiedFiles);

        int targetUpdated = promotionRequestRepository.updateStatusWithVersion(
                promotionId, ReviewTaskStatus.APPROVED, reviewerId, comment, newSkill.getId(), request.getVersion() + 1);
        if (targetUpdated == 0) {
            throw new ConcurrentModificationException("Promotion request target skill was modified concurrently");
        }
        request.setTargetSkillId(newSkill.getId());

        eventPublisher.publishEvent(new SkillPublishedEvent(
                newSkill.getId(), newVersion.getId(), reviewerId));

        return request;
    }

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
        entityManager.detach(request);
        request.setStatus(ReviewTaskStatus.REJECTED);
        request.setReviewedBy(reviewerId);
        request.setReviewComment(comment);
        request.setReviewedAt(Instant.now());
        return request;
    }

    public boolean canViewPromotion(PromotionRequest request, String userId, Set<String> platformRoles) {
        return permissionChecker.canViewPromotion(request, userId, platformRoles);
    }
}
