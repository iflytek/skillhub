package com.iflytek.skillhub.dto;

import java.util.List;

public record AdminNamespaceListResponse(
        List<AdminNamespaceSummaryResponse> items,
        long total,
        int page,
        int size,
        AdminNamespaceListStatsResponse stats
) {}
