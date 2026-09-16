package com.iflytek.skillhub.dto;

import java.util.List;

public record ResourceSearchResponse(
        List<ResourceSummaryResponse> items,
        long total,
        int page,
        int size
) {
}
