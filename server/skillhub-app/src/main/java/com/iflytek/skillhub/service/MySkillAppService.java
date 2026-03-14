package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
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

        List<Long> latestVersionIds = skills.stream()
                .map(Skill::getLatestVersionId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, SkillVersion> versionsById = latestVersionIds.isEmpty()
                ? Map.of()
                : skillVersionRepository.findByIdIn(latestVersionIds).stream()
                        .collect(Collectors.toMap(SkillVersion::getId, Function.identity()));

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
                .map(skill -> toSummaryResponse(skill, versionsById, namespaceSlugsById))
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

        List<Long> latestVersionIds = skillsById.values().stream()
                .map(Skill::getLatestVersionId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, SkillVersion> versionsById = latestVersionIds.isEmpty()
                ? Map.of()
                : skillVersionRepository.findByIdIn(latestVersionIds).stream()
                        .collect(Collectors.toMap(SkillVersion::getId, Function.identity()));

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
                .map(skill -> toSummaryResponse(skill, versionsById, namespaceSlugsById))
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
            Map<Long, SkillVersion> versionsById,
            Map<Long, String> namespaceSlugsById) {
        String latestVersion = skill.getLatestVersionId() == null
                ? null
                : Optional.ofNullable(versionsById.get(skill.getLatestVersionId()))
                        .map(SkillVersion::getVersion)
                        .orElse(null);

        return new SkillSummaryResponse(
                skill.getId(),
                skill.getSlug(),
                skill.getDisplayName(),
                skill.getSummary(),
                skill.getDownloadCount(),
                skill.getStarCount(),
                skill.getRatingAvg(),
                skill.getRatingCount(),
                latestVersion,
                namespaceSlugsById.get(skill.getNamespaceId()),
                skill.getUpdatedAt()
        );
    }
}
