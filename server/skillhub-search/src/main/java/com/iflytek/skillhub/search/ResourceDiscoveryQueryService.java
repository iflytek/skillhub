package com.iflytek.skillhub.search;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Replaceable read-side boundary for searching Skills and Suites together. */
public interface ResourceDiscoveryQueryService {

    ResourcePage search(ResourceQuery query);

    record ResourceQuery(
            String keyword,
            String namespace,
            String resourceType,
            String sort,
            int page,
            int size,
            Set<Long> memberNamespaceIds
    ) {}

    record ResourceHit(
            String resourceType,
            Long id,
            String namespace,
            String slug,
            String displayName,
            String summary,
            String version,
            String visibility,
            long installCount,
            boolean available,
            Instant updatedAt
    ) {}

    record ResourcePage(List<ResourceHit> items, long total, int page, int size) {}
}
