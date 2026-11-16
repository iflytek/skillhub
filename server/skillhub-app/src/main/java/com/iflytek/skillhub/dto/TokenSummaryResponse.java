package com.iflytek.skillhub.dto;

import java.util.List;

public record TokenSummaryResponse(
        Long id,
        String name,
        String tokenPrefix,
        String createdAt,
        String expiresAt,
        String lastUsedAt,
        List<String> capabilities,
        boolean legacyCompat
) {}
