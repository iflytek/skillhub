package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.service.SkillLifecycleProjectionService;
import com.iflytek.skillhub.domain.social.SkillStarRepository;
import com.iflytek.skillhub.domain.social.SkillSubscriptionRepository;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
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
    private final SkillStarRepository skillStarRepository;
    private final SkillSubscriptionRepository skillSubscriptionRepository;
    private final MySkillQueryRepository mySkillQueryRepository;
    private final SkillLifecycleProjectionService skillLifecycleProjectionService;

    public MySkillAppService(
            SkillRepository skillRepository,
            SkillVersionRepository skillVersionRepository,
            SkillStarRepository skillStarRepository,
            SkillSubscriptionRepository skillSubscriptionRepository,
            MySkillQueryRepository mySkillQueryRepository,
            SkillLifecycleProjectionService skillLifecycleProjectionService) {
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillStarRepository = skillStarRepository;
        this.skillSubscriptionRepository = skillSubscriptionRepository;
        this.mySkillQueryRepository = mySkillQueryRepository;
        this.skillLifecycleProjectionService = skillLifecycleProjectionService;
    }

    public PageResponse<SkillSummaryResponse> listMySkills(String userId, int page, int size) {
        return listMySkills(userId, page, size, null, java.util.Set.of());
    }

    public PageResponse<SkillSummaryResponse> listMySkills(String userId,
                                                           int page,
                                                           int size,
                                                           String filter,
                                                           java.util.Set<String> platformRoles) {
        MySkillFilter normalizedFilter = parseFilter(filter);
        Page<Skill> skillPage = normalizedFilter == MySkillFilter.ALL
                ? skillRepository.findByOwnerId(userId, PageRequest.of(page, size))
                : filterSkillsByLifecycle(userId, page, size, normalizedFilter, platformRoles);
        List<SkillSummaryResponse> items = mySkillQueryRepository.getSkillSummaries(skillPage.getContent(), userId);

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
        java.util.Map<Long, Skill> skillsById = skillIds.isEmpty()
                ? java.util.Map.of()
                : skillRepository.findByIdIn(skillIds).stream()
                        .collect(java.util.stream.Collectors.toMap(Skill::getId, java.util.function.Function.identity()));
        List<Skill> orderedSkills = stars.stream()
                .map(star -> skillsById.get(star.getSkillId()))
                .filter(java.util.Objects::nonNull)
                .toList();
        List<SkillSummaryResponse> items = mySkillQueryRepository.getSkillSummaries(orderedSkills, userId);

        return new PageResponse<>(items, starPage.getTotalElements(), starPage.getNumber(), starPage.getSize());
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

        return new PageResponse<>(items, subPage.getTotalElements(), subPage.getNumber(), subPage.getSize());
    }

    private Page<Skill> filterSkillsByLifecycle(String userId,
                                                int page,
                                                int size,
                                                MySkillFilter filter,
                                                java.util.Set<String> platformRoles) {
        List<Skill> skills = skillRepository.findByOwnerId(userId);
        List<Skill> filtered = skills.stream()
                .filter(skill -> matchesFilter(skill, filter, platformRoles))
                .toList();
        int fromIndex = Math.min(page * size, filtered.size());
        int toIndex = Math.min(fromIndex + size, filtered.size());
        return new PageImpl<>(
                filtered.subList(fromIndex, toIndex),
                PageRequest.of(page, size),
                filtered.size()
        );
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
