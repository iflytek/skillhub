package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.search.SearchQuery;
import com.iflytek.skillhub.search.SearchQueryService;
import com.iflytek.skillhub.search.SearchResult;
import com.iflytek.skillhub.search.SearchVisibilityScope;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SkillSearchAppService {

    private final SearchQueryService searchQueryService;
    private final SkillRepository skillRepository;
    private final NamespaceRepository namespaceRepository;
    private final SkillVersionRepository skillVersionRepository;

    public SkillSearchAppService(
            SearchQueryService searchQueryService,
            SkillRepository skillRepository,
            NamespaceRepository namespaceRepository,
            SkillVersionRepository skillVersionRepository) {
        this.searchQueryService = searchQueryService;
        this.skillRepository = skillRepository;
        this.namespaceRepository = namespaceRepository;
        this.skillVersionRepository = skillVersionRepository;
    }

    public record SearchResponse(
            List<SkillSummaryResponse> items,
            long total,
            int page,
            int size
    ) {}

    public SearchResponse search(
            String keyword,
            String namespaceSlug,
            String sortBy,
            int page,
            int size,
            String userId,
            Map<Long, NamespaceRole> userNsRoles) {

        Long namespaceId = resolveNamespaceId(namespaceSlug);

        SearchVisibilityScope scope = buildVisibilityScope(userId, userNsRoles);

        SearchQuery query = new SearchQuery(
                keyword,
                namespaceId,
                scope,
                sortBy != null ? sortBy : "newest",
                page,
                size
        );

        SearchResult result = searchQueryService.search(query);
        List<Skill> matchedSkills = result.skillIds().isEmpty()
                ? List.of()
                : skillRepository.findByIdIn(result.skillIds());
        Map<Long, Skill> skillsById = matchedSkills.stream()
                .collect(Collectors.toMap(Skill::getId, Function.identity()));

        List<Long> latestVersionIds = matchedSkills.stream()
                .map(Skill::getLatestVersionId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, SkillVersion> versionsById = latestVersionIds.isEmpty()
                ? Map.of()
                : skillVersionRepository.findByIdIn(latestVersionIds).stream()
                        .collect(Collectors.toMap(SkillVersion::getId, Function.identity()));

        List<Long> namespaceIds = matchedSkills.stream()
                .map(Skill::getNamespaceId)
                .distinct()
                .toList();
        Map<Long, Namespace> namespacesById = namespaceIds.isEmpty()
                ? Map.of()
                : namespaceRepository.findByIdIn(namespaceIds).stream()
                        .collect(Collectors.toMap(Namespace::getId, Function.identity()));
        Map<Long, String> namespaceSlugsById = namespacesById.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getSlug()));

        List<SkillSummaryResponse> skills = result.skillIds().stream()
                .map(skillsById::get)
                .filter(java.util.Objects::nonNull)
                .filter(skill -> namespaceVisible(skill.getNamespaceId(), namespacesById, userId, userNsRoles))
                .map(skill -> toSummaryResponse(skill, versionsById, namespaceSlugsById))
                .toList();

        return new SearchResponse(skills, skills.size(), result.page(), result.size());
    }

    private Long resolveNamespaceId(String namespaceSlug) {
        if (namespaceSlug == null || namespaceSlug.isBlank()) {
            return null;
        }
        return namespaceRepository.findBySlug(namespaceSlug)
                .map(com.iflytek.skillhub.domain.namespace.Namespace::getId)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.slug.notFound", namespaceSlug));
    }

    private SearchVisibilityScope buildVisibilityScope(String userId, Map<Long, NamespaceRole> userNsRoles) {
        if (userId == null || userNsRoles == null) {
            return SearchVisibilityScope.anonymous();
        }

        Set<Long> memberNamespaceIds = userNsRoles.keySet();
        Set<Long> adminNamespaceIds = userNsRoles.entrySet().stream()
                .filter(e -> e.getValue() == NamespaceRole.ADMIN)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
        adminNamespaceIds.addAll(userNsRoles.entrySet().stream()
                .filter(e -> e.getValue() == NamespaceRole.OWNER)
                .map(Map.Entry::getKey)
                .toList());

        return new SearchVisibilityScope(userId, memberNamespaceIds, adminNamespaceIds);
    }

    private SkillSummaryResponse toSummaryResponse(
            Skill skill,
            Map<Long, SkillVersion> versionsById,
            Map<Long, String> namespaceSlugsById) {
        String latestVersion = skill.getLatestVersionId() == null
                ? null
                : java.util.Optional.ofNullable(versionsById.get(skill.getLatestVersionId()))
                        .map(SkillVersion::getVersion)
                        .orElse(null);
        String namespaceSlug = namespaceSlugsById.get(skill.getNamespaceId());

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
                latestVersion,
                latestVersion == null ? null : "PUBLISHED",
                namespaceSlug,
                skill.getUpdatedAt()
        );
    }

    private boolean namespaceVisible(
            Long namespaceId,
            Map<Long, Namespace> namespacesById,
            String userId,
            Map<Long, NamespaceRole> userNsRoles) {
        NamespaceStatus status = java.util.Optional.ofNullable(namespacesById.get(namespaceId))
                .map(Namespace::getStatus)
                .orElse(NamespaceStatus.ACTIVE);
        if (status != NamespaceStatus.ARCHIVED) {
            return true;
        }
        return userId != null && userNsRoles != null && userNsRoles.containsKey(namespaceId);
    }
}
