package com.iflytek.skillhub.search;

import java.util.List;

/**
 * Immutable search request model shared between application code and search implementations.
 */
public record SearchQuery(
        String keyword,
        Long namespaceId,
        SearchVisibilityScope visibilityScope,
        String sortBy,
        int page,
        int size,
        List<String> labelSlugs
) {
    public SearchQuery(
            String keyword,
            Long namespaceId,
            SearchVisibilityScope visibilityScope,
            String sortBy,
            int page,
            int size) {
        this(keyword, namespaceId, visibilityScope, sortBy, page, size, List.of());
    }
}
