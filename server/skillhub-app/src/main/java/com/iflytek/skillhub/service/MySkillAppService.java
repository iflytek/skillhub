package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.service.SkillLifecycleProjectionService;
import com.iflytek.skillhub.domain.social.SkillStarRepository;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.SkillLifecycleVersionResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Application service that assembles the current user's owned and starred
 * skill lists with lifecycle context.
 */
@Service
public class MySkillAppService {
    private final SkillRepository skillRepository;
    private final NamespaceRepository namespaceRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillStarRepository skillStarRepository;
    private final PromotionRequestRepository promotionRequestRepository;
    private final SkillLifecycleProjectionService skillLifecycleProjectionService;

    public MySkillAppService(
            SkillRepository skillRepository,
            NamespaceRepository namespaceRepository,
            SkillVersionRepository skillVersionRepository,
            SkillStarRepository skillStarRepository,
            PromotionRequestRepository promotionRequestRepository,
            SkillLifecycleProjectionService skillLifecycleProjectionService) {
        this.skillRepository = skillRepository;
        this.namespaceRepository = namespaceRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillStarRepository = skillStarRepository;
        this.promotionRequestRepository = promotionRequestRepository;
        this.skillLifecycleProjectionService = skillLifecycleProjectionService;
    }

    public PageResponse<SkillSummaryResponse> listMySkills(String userId, int page, int size) {
        Page<Skill> skillPage = skillRepository.findByOwnerId(userId, PageRequest.of(page, size));
        List<Skill> skills = skillPage.getContent();

        List<Long> namespaceIds = skills.stream()
                .map(Skill::getNamespaceId)
                .distinct()
                .toList();
        Map<Long, com.iflytek.skillhub.domain.namespace.Namespace> namespacesById = namespaceIds.isEmpty()
                ? Map.of()
                : namespaceRepository.findByIdIn(namespaceIds).stream()
                        .collect(Collectors.toMap(com.iflytek.skillhub.domain.namespace.Namespace::getId, Function.identity()));

        List<SkillSummaryResponse> items = skills.stream()
                .map(skill -> toSummaryResponse(skill, userId, namespacesById))
                .toList();

        return new PageResponse<>(items, skillPage.getTotalElements(), skillPage.getNumber(), skillPage.getSize());
    }

    public PageResponse<SkillSummaryResponse> listMyStars(String userId, int page, int size) {
        Page<com.iflytek.skillhub.domain.social.SkillStar> starPage = skillStarRepository.findByUserId(
                userId,
                PageRequest.of(page, size)
        );
        List<com.iflytek.skillhub.domain.social.SkillStar> stars = starPage.getContent();

        List<Long> skillIds = stars.stream()
                .map(com.iflytek.skillhub.domain.social.SkillStar::getSkillId)
                .distinct()
                .toList();
        Map<Long, Skill> skillsById = skillIds.isEmpty()
                ? Map.of()
                : skillRepository.findByIdIn(skillIds).stream()
                        .collect(Collectors.toMap(Skill::getId, Function.identity()));

        List<Long> namespaceIds = skillsById.values().stream()
                .map(Skill::getNamespaceId)
                .distinct()
                .toList();
        Map<Long, com.iflytek.skillhub.domain.namespace.Namespace> namespacesById = namespaceIds.isEmpty()
                ? Map.of()
                : namespaceRepository.findByIdIn(namespaceIds).stream()
                        .collect(Collectors.toMap(com.iflytek.skillhub.domain.namespace.Namespace::getId, Function.identity()));

        List<SkillSummaryResponse> items = stars.stream()
                .map(star -> skillsById.get(star.getSkillId()))
                .filter(java.util.Objects::nonNull)
                .map(skill -> toSummaryResponse(skill, userId, namespacesById))
                .toList();

        return new PageResponse<>(items, starPage.getTotalElements(), starPage.getNumber(), starPage.getSize());
    }

    private SkillSummaryResponse toSummaryResponse(
            Skill skill,
            String currentUserId,
            Map<Long, com.iflytek.skillhub.domain.namespace.Namespace> namespacesById) {
        com.iflytek.skillhub.domain.namespace.Namespace namespace = namespacesById.get(skill.getNamespaceId());
        SkillLifecycleProjectionService.Projection projection = skillLifecycleProjectionService.projectForViewer(
                skill,
                currentUserId,
                Map.of()
        );
        SkillLifecycleProjectionService.VersionProjection headlineVersion = projection.headlineVersion();
        SkillLifecycleProjectionService.VersionProjection publishedVersion = projection.publishedVersion();
        SkillLifecycleProjectionService.VersionProjection ownerPreviewVersion = projection.ownerPreviewVersion();

        return new SkillSummaryResponse(
                skill.getId(),
                skill.getSlug(),
                skill.getDisplayName(),
                skill.getSummary(),
                skill.getStatus().name(),
                skill.getDownloadCount(),
                skill.getStarCount(),
                skill.getRatingAvg(),
                skill.getRatingCount(),
                namespace != null ? namespace.getSlug() : null,
                skill.getUpdatedAt(),
                canSubmitPromotion(skill, publishedVersion, namespace),
                toLifecycleVersion(headlineVersion),
                toLifecycleVersion(publishedVersion),
                toLifecycleVersion(ownerPreviewVersion),
                projection.resolutionMode().name()
        );
    }

    private boolean canSubmitPromotion(
            Skill skill,
            SkillLifecycleProjectionService.VersionProjection publishedVersion,
            com.iflytek.skillhub.domain.namespace.Namespace namespace) {
        if (namespace == null) {
            return false;
        }
        if (namespace.getType() == NamespaceType.GLOBAL) {
            return false;
        }
        if (namespace.getStatus() != NamespaceStatus.ACTIVE || skill.getStatus() != com.iflytek.skillhub.domain.skill.SkillStatus.ACTIVE) {
            return false;
        }
        if (promotionRequestRepository.findBySourceSkillIdAndStatus(skill.getId(), ReviewTaskStatus.PENDING).isPresent()) {
            return false;
        }
        if (promotionRequestRepository.findBySourceSkillIdAndStatus(skill.getId(), ReviewTaskStatus.APPROVED).isPresent()) {
            return false;
        }
        return publishedVersion != null && "PUBLISHED".equals(publishedVersion.status());
    }

    private SkillLifecycleVersionResponse toLifecycleVersion(SkillLifecycleProjectionService.VersionProjection projection) {
        if (projection == null) {
            return null;
        }
        return new SkillLifecycleVersionResponse(projection.id(), projection.version(), projection.status());
    }
}
