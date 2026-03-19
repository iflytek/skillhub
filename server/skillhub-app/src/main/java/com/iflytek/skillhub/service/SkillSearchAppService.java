package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.skill.service.SkillLifecycleProjectionService;
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

/**
 * Application service that adapts search queries to the search backend and
 * enriches results with authoritative skill metadata and viewer permissions.
 */
@Service
public class SkillSearchAppService {

    private final SearchQueryService searchQueryService;
    private final SkillRepository skillRepository;
    private final NamespaceRepository namespaceRepository;
    private final NamespaceService namespaceService;
    private final VisibilityChecker visibilityChecker;
    private final SkillLifecycleProjectionService skillLifecycleProjectionService;

    public SkillSearchAppService(
            SearchQueryService searchQueryService,
            SkillRepository skillRepository,
            NamespaceRepository namespaceRepository,
            NamespaceService namespaceService,
            VisibilityChecker visibilityChecker,
            SkillLifecycleProjectionService skillLifecycleProjectionService) {
        this.searchQueryService = searchQueryService;
        this.skillRepository = skillRepository;
        this.namespaceRepository = namespaceRepository;
        this.namespaceService = namespaceService;
        this.visibilityChecker = visibilityChecker;
        this.skillLifecycleProjectionService = skillLifecycleProjectionService;
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

        Long namespaceId = resolveNamespaceId(namespaceSlug, userId, userNsRoles);

        SearchVisibilityScope scope = buildVisibilityScope(userId, userNsRoles);

        return searchVisibleSkills(keyword, namespaceId, sortBy != null ? sortBy : "newest", page, size, userId, userNsRoles, scope);
    }

    private Long resolveNamespaceId(String namespaceSlug, String userId, Map<Long, NamespaceRole> userNsRoles) {
        if (namespaceSlug == null || namespaceSlug.isBlank()) {
            return null;
        }
        return namespaceService.getNamespaceBySlugForRead(namespaceSlug, userId, userNsRoles != null ? userNsRoles : Map.of()).getId();
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

    private SearchResponse searchVisibleSkills(
            String keyword,
            Long namespaceId,
            String sortBy,
            int page,
            int size,
            String userId,
            Map<Long, NamespaceRole> userNsRoles,
            SearchVisibilityScope scope) {
        int batchSize = Math.max(size, 20);
        long rawTotal = Long.MAX_VALUE;
        int rawPage = 0;
        long visibleSeen = 0;
        int visibleStart = page * size;
        List<SkillSummaryResponse> pageItems = new java.util.ArrayList<>();

        while ((long) rawPage * batchSize < rawTotal) {
            SearchResult result = searchQueryService.search(new SearchQuery(
                    keyword,
                    namespaceId,
                    scope,
                    sortBy,
                    rawPage,
                    batchSize
            ));
            rawTotal = result.total();
            List<SkillSummaryResponse> visibleBatch = mapVisibleSkillSummaries(result.skillIds(), userId, userNsRoles);
            for (SkillSummaryResponse item : visibleBatch) {
                if (visibleSeen >= visibleStart && pageItems.size() < size) {
                    pageItems.add(item);
                }
                visibleSeen++;
            }
            if (result.skillIds().isEmpty()) {
                break;
            }
            rawPage++;
        }

        return new SearchResponse(pageItems, visibleSeen, page, size);
    }

    private List<SkillSummaryResponse> mapVisibleSkillSummaries(
            List<Long> skillIds,
            String userId,
            Map<Long, NamespaceRole> userNsRoles) {
        if (skillIds.isEmpty()) {
            return List.of();
        }

        List<Skill> matchedSkills = skillRepository.findByIdIn(skillIds);
        Map<Long, Skill> skillsById = matchedSkills.stream()
                .collect(Collectors.toMap(Skill::getId, Function.identity()));

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

        return skillIds.stream()
                .map(skillsById::get)
                .filter(java.util.Objects::nonNull)
                .filter(skill -> visibilityChecker.canAccess(skill, userId, userNsRoles != null ? userNsRoles : Map.of()))
                .filter(skill -> namespaceVisible(skill.getNamespaceId(), namespacesById, userId, userNsRoles))
                .map(skill -> toSummaryResponse(skill, namespaceSlugsById))
                .toList();
    }

    private SkillSummaryResponse toSummaryResponse(
            Skill skill,
            Map<Long, String> namespaceSlugsById) {
        SkillLifecycleProjectionService.Projection projection = skillLifecycleProjectionService.projectForViewer(
                skill,
                null,
                Map.of()
        );
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
                namespaceSlug,
                skill.getUpdatedAt(),
                false,
                toLifecycleVersion(projection.headlineVersion()),
                toLifecycleVersion(projection.publishedVersion()),
                toLifecycleVersion(projection.ownerPreviewVersion()),
                projection.resolutionMode().name()
        );
    }

    private com.iflytek.skillhub.dto.SkillLifecycleVersionResponse toLifecycleVersion(
            SkillLifecycleProjectionService.VersionProjection projection) {
        if (projection == null) {
            return null;
        }
        return new com.iflytek.skillhub.dto.SkillLifecycleVersionResponse(
                projection.id(),
                projection.version(),
                projection.status()
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
