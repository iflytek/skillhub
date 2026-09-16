package com.iflytek.skillhub.service;

import com.iflytek.skillhub.dto.ResourceSearchResponse;
import com.iflytek.skillhub.dto.ResourceSummaryResponse;
import com.iflytek.skillhub.search.ResourceDiscoveryQueryService;
import com.iflytek.skillhub.search.ResourceDiscoveryQueryService.ResourceQuery;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Maps the replaceable resource-search result into the public API projection. */
@Service
public class ResourceDiscoveryAppService {

    private final ResourceDiscoveryQueryService queryService;

    public ResourceDiscoveryAppService(ResourceDiscoveryQueryService queryService) {
        this.queryService = queryService;
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
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 100);
        var result = queryService.search(new ResourceQuery(
                keyword, namespace, resourceType, sort, safePage, safeSize, memberNamespaceIds));
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
                        item.updatedAt())).toList(),
                result.total(), result.page(), result.size());
    }
}
