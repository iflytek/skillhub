package com.iflytek.skillhub.dto;

import java.util.List;

public record TokenCreateResponse(
        String token,
        Long id,
        String name,
        String tokenPrefix,
        String createdAt,
        String expiresAt,
        List<String> capabilities,
        boolean legacyCompat,
        boolean deprecatedDefault
) {}
