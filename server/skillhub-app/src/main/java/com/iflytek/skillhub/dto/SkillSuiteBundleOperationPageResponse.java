package com.iflytek.skillhub.dto;

import java.util.List;

/** One prioritized page of Bundle tasks plus collection-wide polling state. */
public record SkillSuiteBundleOperationPageResponse(
        List<SkillSuiteBundleOperationSummaryResponse> items,
        long total,
        int page,
        int size,
        boolean hasChangingOperations
) {
}
