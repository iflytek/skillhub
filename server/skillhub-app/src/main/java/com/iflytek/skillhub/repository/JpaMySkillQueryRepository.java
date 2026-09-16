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
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.SkillLifecycleVersionResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;

@Repository
public class JpaMySkillQueryRepository implements MySkillQueryRepository {

    private final NamespaceRepository namespaceRepository;
    private final PromotionRequestRepository promotionRequestRepository;
    private final SkillLifecycleProjectionService skillLifecycleProjectionService;
    private final UserAccountRepository userAccountRepository;

    public JpaMySkillQueryRepository(NamespaceRepository namespaceRepository,
                                     PromotionRequestRepository promotionRequestRepository,
                                     SkillLifecycleProjectionService skillLifecycleProjectionService) {
        this(namespaceRepository, promotionRequestRepository, skillLifecycleProjectionService, null);
    }

    @Autowired
    public JpaMySkillQueryRepository(NamespaceRepository namespaceRepository,
                                     PromotionRequestRepository promotionRequestRepository,
                                     SkillLifecycleProjectionService skillLifecycleProjectionService,
                                     UserAccountRepository userAccountRepository) {
        this.namespaceRepository = namespaceRepository;
        this.promotionRequestRepository = promotionRequestRepository;
        this.skillLifecycleProjectionService = skillLifecycleProjectionService;
        this.userAccountRepository = userAccountRepository;
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
        Map<String, UserAccount> ownersById = userAccountRepository == null
                ? Map.of()
                : userAccountRepository.findByIdIn(skills.stream().map(Skill::getOwnerId).distinct().toList())
                .stream().collect(Collectors.toMap(UserAccount::getId, Function.identity()));
        return skills.stream()
                .map(skill -> toSummaryResponse(skill, currentUserId, namespacesById, ownersById))
                .toList();
    }

    @Override
    public List<SkillSummaryResponse> getHiddenSkillSummaries(List<Skill> skills) {
        if (skills.isEmpty()) {
            return List.of();
        }
        Map<Long, Namespace> namespacesById = namespaceRepository.findByIdIn(
                        skills.stream().map(Skill::getNamespaceId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Namespace::getId, Function.identity()));
        Map<String, UserAccount> ownersById = userAccountRepository == null
                ? Map.of()
                : userAccountRepository.findByIdIn(skills.stream().map(Skill::getOwnerId).distinct().toList())
                .stream().collect(Collectors.toMap(UserAccount::getId, Function.identity()));
        Map<Long, SkillLifecycleProjectionService.Projection> projections =
                skillLifecycleProjectionService.projectPublishedSummaries(skills);

        return skills.stream()
                .map(skill -> toSummaryResponse(
                        skill,
                        namespacesById,
                        ownersById,
                        projections.get(skill.getId()),
                        false
                ))
                .toList();
    }

    private SkillSummaryResponse toSummaryResponse(Skill skill,
                                                   String currentUserId,
                                                   Map<Long, Namespace> namespacesById,
                                                   Map<String, UserAccount> ownersById) {
        Namespace namespace = namespacesById.get(skill.getNamespaceId());
        SkillLifecycleProjectionService.Projection projection = skillLifecycleProjectionService.projectForViewer(
                skill,
                currentUserId,
                Map.of()
        );
        if (skill.getOwnerId().equals(currentUserId)) {
            projection = skillLifecycleProjectionService.projectForOwnerSummary(skill);
        }
        return toSummaryResponse(
                skill,
                namespacesById,
                ownersById,
                projection,
                canSubmitPromotion(skill, projection.publishedVersion(), namespace)
        );
    }

    private SkillSummaryResponse toSummaryResponse(Skill skill,
                                                   Map<Long, Namespace> namespacesById,
                                                   Map<String, UserAccount> ownersById,
                                                   SkillLifecycleProjectionService.Projection projection,
                                                   boolean canSubmitPromotion) {
        Namespace namespace = namespacesById.get(skill.getNamespaceId());
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
                skill.getOwnerId(),
                ownersById.get(skill.getOwnerId()) != null
                        ? ownersById.get(skill.getOwnerId()).getDisplayName()
                        : null,
                canSubmitPromotion,
                toLifecycleVersion(headlineVersion),
                toLifecycleVersion(publishedVersion),
                toLifecycleVersion(ownerPreviewVersion),
                projection.resolutionMode().name(),
                null,
                null
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
