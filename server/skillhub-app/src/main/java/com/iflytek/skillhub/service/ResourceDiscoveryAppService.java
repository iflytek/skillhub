package com.iflytek.skillhub.service;

import com.iflytek.skillhub.dto.ResourceSearchResponse;
import com.iflytek.skillhub.dto.ResourceSummaryResponse;
import com.iflytek.skillhub.dto.SkillLabelDto;
import com.iflytek.skillhub.search.ResourceDiscoveryQueryService;
import com.iflytek.skillhub.search.ResourceDiscoveryQueryService.ResourceQuery;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Maps the replaceable resource-search result into the public API projection. */
@Service
public class ResourceDiscoveryAppService {

    private final ResourceDiscoveryQueryService queryService;
    private final SkillSuiteLabelProjectionService suiteLabelProjectionService;

    public ResourceDiscoveryAppService(
            ResourceDiscoveryQueryService queryService,
            SkillSuiteLabelProjectionService suiteLabelProjectionService
    ) {
        this.queryService = queryService;
        this.suiteLabelProjectionService = suiteLabelProjectionService;
    }

    public ResourceSearchResponse search(
            String keyword,
            String namespace,
            String resourceType,
            String sort,
            int page,
            int size,
            Set<Long> memberNamespaceIds
    ) {
        return search(keyword, namespace, resourceType, sort, page, size, memberNamespaceIds, List.of());
    }

    public ResourceSearchResponse search(
            String keyword,
            String namespace,
            String resourceType,
            String sort,
            int page,
            int size,
            Set<Long> memberNamespaceIds,
            List<String> labelSlugs
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 100);
        var result = queryService.search(new ResourceQuery(
                keyword, namespace, resourceType, sort, safePage, safeSize, memberNamespaceIds,
                normalizeLabelSlugs(labelSlugs)));
        Map<Long, List<SkillLabelDto>> labelsBySuiteId =
                suiteLabelProjectionService.labelsBySuiteIds(result.items().stream()
                        .filter(item -> "SUITE".equals(item.resourceType()))
                        .map(ResourceDiscoveryQueryService.ResourceHit::id)
                        .toList());
        return new ResourceSearchResponse(
                result.items().stream().map(item -> new ResourceSummaryResponse(
                        item.resourceType(),
                        "/" + ("SUITE".equals(item.resourceType()) ? "suite" : "space")
                                + "/" + item.namespace() + "/" + item.slug(),
                        item.id(),
                        item.namespace(),
                        item.slug(),
                        item.displayName(),
                        item.summary(),
                        item.version(),
                        item.visibility(),
                        item.installCount(),
                        item.available(),
                        item.updatedAt(),
                        "SUITE".equals(item.resourceType())
                                ? labelsBySuiteId.getOrDefault(item.id(), List.of())
                                : List.of())).toList(),
                result.total(), result.page(), result.size());
    }

    private List<String> normalizeLabelSlugs(List<String> labelSlugs) {
        if (labelSlugs == null || labelSlugs.isEmpty()) {
            return List.of();
        }
        return labelSlugs.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }
}
