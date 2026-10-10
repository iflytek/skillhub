package com.iflytek.skillhub.dto;

import java.util.List;

/**
 * Author-facing review progress page with search-scoped current-status totals.
 */
public record ReviewProgressPageResponse(
        List<ReviewProgressResponse> items,
        long total,
        int page,
        int size,
        ReviewProgressStatusCounts statusCounts
) {}
