package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.skill.SkillOwnerAccessPolicy;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.repository.StarredSkillQueryRepository;
import java.util.Map;
import java.util.Set;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.service.SkillLifecycleProjectionService;
import com.iflytek.skillhub.domain.social.SkillSubscriptionRepository;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.repository.HiddenSkillQueryRepository;
import com.iflytek.skillhub.repository.MySkillQueryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
/**
 * Application service that assembles the current user's owned and starred
 * skill lists with lifecycle context.
 */
@Service
public class MySkillAppService {
    private final SkillOwnerAccessPolicy ownerAccess;
    public enum MySkillFilter {
        ALL,
        PENDING_REVIEW,
        PUBLISHED,
        REJECTED,
        ARCHIVED,
        HIDDEN
    }

    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final StarredSkillQueryRepository starredSkillQueryRepository;
    private final SkillSubscriptionRepository skillSubscriptionRepository;
    private final MySkillQueryRepository mySkillQueryRepository;
    private final HiddenSkillQueryRepository hiddenSkillQueryRepository;
    private final SkillLifecycleProjectionService skillLifecycleProjectionService;
    private final NamespaceRepository namespaceRepository;
    private final SkillSuiteMembershipProjectionService suiteMembershipProjection;

    public MySkillAppService(
            SkillOwnerAccessPolicy ownerAccess,
            SkillRepository skillRepository,
            SkillVersionRepository skillVersionRepository,
            StarredSkillQueryRepository starredSkillQueryRepository,
            SkillSubscriptionRepository skillSubscriptionRepository,
            MySkillQueryRepository mySkillQueryRepository,
            HiddenSkillQueryRepository hiddenSkillQueryRepository,
            SkillLifecycleProjectionService skillLifecycleProjectionService,
            NamespaceRepository namespaceRepository) {
        this(ownerAccess, skillRepository, skillVersionRepository, starredSkillQueryRepository,
                skillSubscriptionRepository, mySkillQueryRepository, hiddenSkillQueryRepository,
                skillLifecycleProjectionService, namespaceRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public MySkillAppService(
            SkillOwnerAccessPolicy ownerAccess,
            SkillRepository skillRepository,
            SkillVersionRepository skillVersionRepository,
            StarredSkillQueryRepository starredSkillQueryRepository,
            SkillSubscriptionRepository skillSubscriptionRepository,
            MySkillQueryRepository mySkillQueryRepository,
            HiddenSkillQueryRepository hiddenSkillQueryRepository,
            SkillLifecycleProjectionService skillLifecycleProjectionService,
            NamespaceRepository namespaceRepository,
            SkillSuiteMembershipProjectionService suiteMembershipProjection) {
        this.suiteMembershipProjection = suiteMembershipProjection;
        this.ownerAccess = ownerAccess;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.starredSkillQueryRepository = starredSkillQueryRepository;
        this.skillSubscriptionRepository = skillSubscriptionRepository;
        this.mySkillQueryRepository = mySkillQueryRepository;
        this.hiddenSkillQueryRepository = hiddenSkillQueryRepository;
        this.skillLifecycleProjectionService = skillLifecycleProjectionService;
        this.namespaceRepository = namespaceRepository;
    }

    public PageResponse<SkillSummaryResponse> listMySkills(String userId, int page, int size) {
        return listMySkills(userId, page, size, null, null, null, java.util.Set.of());
    }

    public PageResponse<SkillSummaryResponse> listMySkills(String userId,
                                                           int page,
                                                           int size,
                                                           String filter,
                                                           java.util.Set<String> platformRoles) {
        return listMySkills(userId, page, size, filter, null, null, platformRoles);
    }

    public PageResponse<SkillSummaryResponse> listMySkills(String userId,
                                                           int page,
                                                           int size,
                                                           String filter,
                                                           String keyword,
                                                           String namespace,
                                                           java.util.Set<String> platformRoles) {
        MySkillFilter normalizedFilter = parseFilter(filter);

        Page<Skill> skillPage;
        if (normalizedFilter == MySkillFilter.PENDING_REVIEW) {
            skillPage = mySkillQueryRepository.findPendingSkills(userId, keyword, resolveNamespaceId(namespace), PageRequest.of(page, size));
        } else if (normalizedFilter == MySkillFilter.HIDDEN) {
            skillPage = listHiddenSkills(page, size, keyword, namespace, platformRoles);
        } else if (normalizedFilter == MySkillFilter.ALL
                && (keyword == null || keyword.isBlank())
                && (namespace == null || namespace.isBlank())) {
            skillPage = skillRepository.findVisibleByOwnerId(userId, PageRequest.of(page, size));
        } else {
            skillPage = filterSkills(userId, page, size, normalizedFilter, keyword, namespace, platformRoles);
        }

        List<SkillSummaryResponse> items = normalizedFilter == MySkillFilter.HIDDEN
                ? mySkillQueryRepository.getHiddenSkillSummaries(skillPage.getContent())
                : mySkillQueryRepository.getSkillSummaries(skillPage.getContent(), userId);

        if (normalizedFilter != MySkillFilter.HIDDEN) {
            var managementStates = mySkillQueryRepository.getManagementStates(skillPage.getContent(), userId);
            Map<Long, Skill> skillsById = skillPage.getContent().stream()
                    .collect(java.util.stream.Collectors.toMap(Skill::getId, skill -> skill));
            items = items.stream().map(item -> {
                var state = managementStates.get(item.id());
                boolean readOnly = ownerAccess.isReadOnly(skillsById.get(item.id()), userId, platformRoles);
                return item.withManagementState(new SkillSummaryResponse.ManagementState(
                        state == null ? null : state.requestedVisibility(),
                        state == null ? null : state.reviewTaskId(),
                        state == null ? null : state.promotionRole(),
                        state == null ? null : state.relatedNamespace(),
                        state == null ? null : state.relatedSlug(), readOnly));
            }).toList();
        }
        return new PageResponse<>(withSuiteMemberships(items, userId), skillPage.getTotalElements(), skillPage.getNumber(), skillPage.getSize());
    }

    private List<SkillSummaryResponse> withSuiteMemberships(List<SkillSummaryResponse> items, String userId) {
        return suiteMembershipProjection == null ? items : suiteMembershipProjection.enrich(items, userId);
    }

    private Page<Skill> listHiddenSkills(int page,
                                         int size,
                                         String keyword,
                                         String namespace,
                                         java.util.Set<String> platformRoles) {
        PageRequest pageable = PageRequest.of(page, size);
        if (!platformRoles.contains("SUPER_ADMIN")) {
            return Page.empty(pageable);
        }
        Long namespaceId = resolveNamespaceId(namespace);
        return hiddenSkillQueryRepository.search(normalizeKeyword(keyword), namespaceId, pageable);
    }

    public PageResponse<SkillSummaryResponse> listMyStars(String userId, int page, int size,
                                                         Map<Long, NamespaceRole> namespaceRoles,
                                                         Set<String> platformRoles) {
        Page<Skill> skills = starredSkillQueryRepository.findAccessible(
                userId, namespaceRoles, platformRoles, PageRequest.of(page, size));
        List<SkillSummaryResponse> items = mySkillQueryRepository.getSkillSummaries(skills.getContent(), userId);
        return new PageResponse<>(withSuiteMemberships(items, userId), skills.getTotalElements(), skills.getNumber(), skills.getSize());
    }

    public PageResponse<SkillSummaryResponse> listMySubscriptions(String userId, int page, int size) {
        Page<com.iflytek.skillhub.domain.social.SkillSubscription> subPage = skillSubscriptionRepository.findByUserId(
                userId,
                PageRequest.of(page, size)
        );
        List<com.iflytek.skillhub.domain.social.SkillSubscription> subs = subPage.getContent();

        List<Long> skillIds = subs.stream()
                .map(com.iflytek.skillhub.domain.social.SkillSubscription::getSkillId)
                .distinct()
                .toList();
        java.util.Map<Long, Skill> skillsById = skillIds.isEmpty()
                ? java.util.Map.of()
                : skillRepository.findByIdIn(skillIds).stream()
                        .collect(java.util.stream.Collectors.toMap(Skill::getId, java.util.function.Function.identity()));
        List<Skill> orderedSkills = subs.stream()
                .map(sub -> skillsById.get(sub.getSkillId()))
                .filter(java.util.Objects::nonNull)
                .toList();
        List<SkillSummaryResponse> items = mySkillQueryRepository.getSkillSummaries(orderedSkills, userId);

        return new PageResponse<>(withSuiteMemberships(items, userId), subPage.getTotalElements(), subPage.getNumber(), subPage.getSize());
    }

    private Page<Skill> filterSkills(String userId,
                                     int page,
                                     int size,
                                     MySkillFilter filter,
                                     String keyword,
                                     String namespace,
                                     java.util.Set<String> platformRoles) {
        List<Skill> skills = skillRepository.findByOwnerId(userId);

        // Namespace filter
        Long namespaceId = resolveNamespaceId(namespace);

        final Long finalNamespaceId = namespaceId;
        String normalizedKeyword = normalizeKeyword(keyword);

        List<Skill> filtered = skills.stream()
                .filter(skill -> matchesNamespace(skill, finalNamespaceId))
                .filter(skill -> matchesKeyword(skill, normalizedKeyword))
                .filter(skill -> matchesFilter(skill, filter, platformRoles))
                .toList();

        return page(filtered, page, size);
    }

    private Long resolveNamespaceId(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return null;
        }
        return namespaceRepository.findBySlug(namespace.trim())
                .map(Namespace::getId)
                .orElse(-1L);
    }

    private String normalizeKeyword(String keyword) {
        return keyword != null && !keyword.isBlank()
                ? keyword.trim().toLowerCase(java.util.Locale.ROOT)
                : null;
    }

    private Page<Skill> page(List<Skill> skills, int page, int size) {
        int fromIndex = Math.min(page * size, skills.size());
        int toIndex = Math.min(fromIndex + size, skills.size());
        return new PageImpl<>(skills.subList(fromIndex, toIndex), PageRequest.of(page, size), skills.size());
    }

    private boolean matchesNamespace(Skill skill, Long namespaceId) {
        if (namespaceId == null) {
            return true;
        }
        if (namespaceId == -1L) {
            return false;
        }
        return skill.getNamespaceId().equals(namespaceId);
    }

    private boolean matchesKeyword(Skill skill, String keyword) {
        if (keyword == null) {
            return true;
        }
        String displayName = skill.getDisplayName() != null ? skill.getDisplayName().toLowerCase(java.util.Locale.ROOT) : "";
        String slug = skill.getSlug() != null ? skill.getSlug().toLowerCase(java.util.Locale.ROOT) : "";
        String summary = skill.getSummary() != null ? skill.getSummary().toLowerCase(java.util.Locale.ROOT) : "";

        return displayName.contains(keyword) || slug.contains(keyword) || summary.contains(keyword);
    }

    private Page<Skill> filterSkillsByLifecycle(String userId,
                                                int page,
                                                int size,
                                                MySkillFilter filter,
                                                java.util.Set<String> platformRoles) {
        return filterSkills(userId, page, size, filter, null, null, platformRoles);
    }

    private boolean matchesFilter(Skill skill, MySkillFilter filter, java.util.Set<String> platformRoles) {
        if (filter == MySkillFilter.HIDDEN) {
            return platformRoles.contains("SUPER_ADMIN") && skill.isHidden();
        }

        if (skill.isHidden()) {
            return false;
        }
        if (filter == MySkillFilter.ARCHIVED) {
            return skill.getStatus() == com.iflytek.skillhub.domain.skill.SkillStatus.ARCHIVED;
        }
        if (skill.getStatus() == com.iflytek.skillhub.domain.skill.SkillStatus.ARCHIVED) {
            return false;
        }

        SkillLifecycleProjectionService.Projection projection = skillLifecycleProjectionService.projectForOwnerSummary(skill);
        SkillLifecycleProjectionService.VersionProjection ownerPreviewVersion = projection.ownerPreviewVersion();
        SkillLifecycleProjectionService.VersionProjection publishedVersion = projection.publishedVersion();

        return switch (filter) {
            case PENDING_REVIEW -> ownerPreviewVersion != null && "PENDING_REVIEW".equals(ownerPreviewVersion.status());
            case PUBLISHED -> publishedVersion != null;
            case REJECTED -> ownerPreviewVersion != null && "REJECTED".equals(ownerPreviewVersion.status());
            case ALL, ARCHIVED, HIDDEN -> true;
        };
    }

    private MySkillFilter parseFilter(String filter) {
        if (filter == null || filter.isBlank()) {
            return MySkillFilter.ALL;
        }
        try {
            return MySkillFilter.valueOf(filter.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return MySkillFilter.ALL;
        }
    }
}
