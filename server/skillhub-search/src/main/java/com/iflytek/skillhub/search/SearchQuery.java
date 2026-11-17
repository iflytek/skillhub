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
        List<String> labelSlugs,
        boolean requireInstallableLatest,
        SkillSearchSource source
) {
    public SearchQuery {
        source = source == null ? SkillSearchSource.ALL : source;
    }

    public SearchQuery(
            String keyword, Long namespaceId, SearchVisibilityScope visibilityScope,
            String sortBy, int page, int size, List<String> labelSlugs,
            boolean requireInstallableLatest) {
        this(keyword, namespaceId, visibilityScope, sortBy, page, size, labelSlugs,
                requireInstallableLatest, SkillSearchSource.ALL);
    }

    public SearchQuery(
            String keyword,
            Long namespaceId,
            SearchVisibilityScope visibilityScope,
            String sortBy,
            int page,
            int size,
            List<String> labelSlugs) {
        this(keyword, namespaceId, visibilityScope, sortBy, page, size, labelSlugs, false);
    }

    public SearchQuery(
            String keyword,
            Long namespaceId,
            SearchVisibilityScope visibilityScope,
            String sortBy,
            int page,
            int size) {
        this(keyword, namespaceId, visibilityScope, sortBy, page, size, List.of(), false);
    }
}
