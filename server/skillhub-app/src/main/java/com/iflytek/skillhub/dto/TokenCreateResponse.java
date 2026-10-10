package com.iflytek.skillhub.dto;

public record TokenCreateResponse(
        String token,
        Long id,
        String name,
        String tokenPrefix,
        String createdAt,
        String expiresAt
) {}
