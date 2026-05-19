package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.service.SkillLifecycleProjectionService;
import com.iflytek.skillhub.dto.SkillLifecycleVersionResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class JpaMySkillQueryRepository implements MySkillQueryRepository {

    private final NamespaceRepository namespaceRepository;
    private final PromotionRequestRepository promotionRequestRepository;
    private final SkillLifecycleProjectionService skillLifecycleProjectionService;

    public JpaMySkillQueryRepository(NamespaceRepository namespaceRepository,
                                     PromotionRequestRepository promotionRequestRepository,
                                     SkillLifecycleProjectionService skillLifecycleProjectionService) {
        this.namespaceRepository = namespaceRepository;
        this.promotionRequestRepository = promotionRequestRepository;
        this.skillLifecycleProjectionService = skillLifecycleProjectionService;
    }

    @Override
    public List<SkillSummaryResponse> getSkillSummaries(List<Skill> skills, String currentUserId) {
        if (skills.isEmpty()) {
            return List.of();
        }
        Map<Long, Namespace> namespacesById = namespaceRepository.findByIdIn(
                        skills.stream().map(Skill::getNamespaceId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Namespace::getId, Function.identity()));
        return skills.stream()
                .map(skill -> toSummaryResponse(skill, currentUserId, namespacesById))
                .toList();
    }

    private SkillSummaryResponse toSummaryResponse(Skill skill,
                                                   String currentUserId,
                                                   Map<Long, Namespace> namespacesById) {
        Namespace namespace = namespacesById.get(skill.getNamespaceId());
        SkillLifecycleProjectionService.Projection projection = skillLifecycleProjectionService.projectForViewer(
                skill,
                currentUserId,
                Map.of()
        );
        if (skill.getOwnerId().equals(currentUserId)) {
            projection = skillLifecycleProjectionService.projectForOwnerSummary(skill);
        }
        SkillLifecycleProjectionService.VersionProjection headlineVersion = projection.headlineVersion();
        SkillLifecycleProjectionService.VersionProjection publishedVersion = projection.publishedVersion();
        SkillLifecycleProjectionService.VersionProjection ownerPreviewVersion = projection.ownerPreviewVersion();

        return new SkillSummaryResponse(
                skill.getId(),
                skill.getSlug(),
                skill.getDisplayName(),
                skill.getSummary(),
                skill.getVisibility().name(),
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

    private boolean canSubmitPromotion(Skill skill,
                                       SkillLifecycleProjectionService.VersionProjection publishedVersion,
                                       Namespace namespace) {
        if (namespace == null) {
            return false;
        }
        if (namespace.getType() == NamespaceType.GLOBAL) {
            return false;
        }
        if (namespace.getStatus() != NamespaceStatus.ACTIVE || skill.getStatus() != SkillStatus.ACTIVE) {
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
