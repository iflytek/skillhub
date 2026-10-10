package com.iflytek.skillhub.dto;

public record TokenSummaryResponse(
        Long id,
        String name,
        String tokenPrefix,
        String createdAt,
        String expiresAt,
        String lastUsedAt
) {}
