package com.iflytek.skillhub.dto;

public record AuthProviderResponse(
        String id,
        String name,
        String authorizationUrl
) {}
