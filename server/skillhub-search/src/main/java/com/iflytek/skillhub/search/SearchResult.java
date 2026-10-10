package com.iflytek.skillhub.search;

import java.util.List;

/**
 * Compact search response containing matching skill identifiers and pagination metadata.
 */
public record SearchResult(
        List<Long> skillIds,
        long total,
        int page,
        int size
) {}
