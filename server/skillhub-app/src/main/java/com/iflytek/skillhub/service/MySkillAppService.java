package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.social.SkillStarRepository;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MySkillAppService {
    private static final int STAR_PAGE_SIZE = 200;

    private final SkillRepository skillRepository;
    private final NamespaceRepository namespaceRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillStarRepository skillStarRepository;

    public MySkillAppService(
            SkillRepository skillRepository,
            NamespaceRepository namespaceRepository,
            SkillVersionRepository skillVersionRepository,
            SkillStarRepository skillStarRepository) {
        this.skillRepository = skillRepository;
        this.namespaceRepository = namespaceRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillStarRepository = skillStarRepository;
    }

    public List<SkillSummaryResponse> listMySkills(String userId) {
        List<Skill> skills = skillRepository.findByOwnerId(userId).stream()
                .sorted(Comparator.comparing(Skill::getUpdatedAt).reversed())
                .toList();

        Map<Long, SkillVersion> versionsBySkillId = loadLatestRelevantVersions(skills);

        List<Long> namespaceIds = skills.stream()
                .map(Skill::getNamespaceId)
                .distinct()
                .toList();
        Map<Long, String> namespaceSlugsById = namespaceIds.isEmpty()
                ? Map.of()
                : namespaceRepository.findByIdIn(namespaceIds).stream()
                        .collect(Collectors.toMap(
                                com.iflytek.skillhub.domain.namespace.Namespace::getId,
                                com.iflytek.skillhub.domain.namespace.Namespace::getSlug));

        return skills.stream()
                .map(skill -> toSummaryResponse(skill, versionsBySkillId, namespaceSlugsById))
                .toList();
    }

    public List<SkillSummaryResponse> listMyStars(String userId) {
        List<com.iflytek.skillhub.domain.social.SkillStar> stars = loadAllStars(userId);

        List<Long> skillIds = stars.stream()
                .map(com.iflytek.skillhub.domain.social.SkillStar::getSkillId)
                .distinct()
                .toList();
        Map<Long, Skill> skillsById = skillIds.isEmpty()
                ? Map.of()
                : skillRepository.findByIdIn(skillIds).stream()
                        .collect(Collectors.toMap(Skill::getId, Function.identity()));

        Map<Long, SkillVersion> versionsBySkillId = loadLatestRelevantVersions(skillsById.values());

        List<Long> namespaceIds = skillsById.values().stream()
                .map(Skill::getNamespaceId)
                .distinct()
                .toList();
        Map<Long, String> namespaceSlugsById = namespaceIds.isEmpty()
                ? Map.of()
                : namespaceRepository.findByIdIn(namespaceIds).stream()
                        .collect(Collectors.toMap(
                                com.iflytek.skillhub.domain.namespace.Namespace::getId,
                                com.iflytek.skillhub.domain.namespace.Namespace::getSlug));

        return stars.stream()
                .sorted(Comparator.comparing(com.iflytek.skillhub.domain.social.SkillStar::getCreatedAt).reversed())
                .map(star -> skillsById.get(star.getSkillId()))
                .filter(java.util.Objects::nonNull)
                .map(skill -> toSummaryResponse(skill, versionsBySkillId, namespaceSlugsById))
                .toList();
    }

    private List<com.iflytek.skillhub.domain.social.SkillStar> loadAllStars(String userId) {
        List<com.iflytek.skillhub.domain.social.SkillStar> stars = new java.util.ArrayList<>();
        int pageNumber = 0;

        while (true) {
            Page<com.iflytek.skillhub.domain.social.SkillStar> page = skillStarRepository.findByUserId(
                    userId,
                    PageRequest.of(pageNumber, STAR_PAGE_SIZE)
            );
            stars.addAll(page.getContent());

            if (!page.hasNext()) {
                return stars;
            }
            pageNumber++;
        }
    }

    private SkillSummaryResponse toSummaryResponse(
            Skill skill,
            Map<Long, SkillVersion> versionsBySkillId,
            Map<Long, String> namespaceSlugsById) {
        SkillVersion latestVersion = versionsBySkillId.get(skill.getId());

        return new SkillSummaryResponse(
                skill.getId(),
                skill.getSlug(),
                skill.getDisplayName(),
                skill.getSummary(),
                skill.getDownloadCount(),
                skill.getStarCount(),
                skill.getRatingAvg(),
                skill.getRatingCount(),
                Optional.ofNullable(latestVersion).map(SkillVersion::getVersion).orElse(null),
                Optional.ofNullable(latestVersion).map(SkillVersion::getStatus).map(Enum::name).orElse(null),
                namespaceSlugsById.get(skill.getNamespaceId()),
                skill.getUpdatedAt()
        );
    }

    private Map<Long, SkillVersion> loadLatestRelevantVersions(java.util.Collection<Skill> skills) {
        if (skills.isEmpty()) {
            return Map.of();
        }

        List<Long> explicitLatestVersionIds = skills.stream()
                .map(Skill::getLatestVersionId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, SkillVersion> versionsById = explicitLatestVersionIds.isEmpty()
                ? Map.of()
                : skillVersionRepository.findByIdIn(explicitLatestVersionIds).stream()
                        .collect(Collectors.toMap(SkillVersion::getId, Function.identity()));

        List<Long> skillIdsNeedingFallback = skills.stream()
                .filter(skill -> skill.getLatestVersionId() == null || !versionsById.containsKey(skill.getLatestVersionId()))
                .map(Skill::getId)
                .distinct()
                .toList();
        Map<Long, SkillVersion> fallbackBySkillId = skillIdsNeedingFallback.isEmpty()
                ? Map.of()
                : skillVersionRepository.findBySkillIdIn(skillIdsNeedingFallback).stream()
                        .filter(version -> version.getStatus() != SkillVersionStatus.YANKED)
                        .collect(Collectors.toMap(
                                SkillVersion::getSkillId,
                                Function.identity(),
                                (left, right) -> left.getCreatedAt().isAfter(right.getCreatedAt()) ? left : right
                        ));

        Map<Long, SkillVersion> resolvedVersions = new java.util.HashMap<>();
        for (Skill skill : skills) {
            SkillVersion resolvedVersion = skill.getLatestVersionId() != null
                    ? versionsById.get(skill.getLatestVersionId())
                    : fallbackBySkillId.get(skill.getId());
            if (resolvedVersion != null) {
                resolvedVersions.put(skill.getId(), resolvedVersion);
            }
        }
        return resolvedVersions;
    }
}
