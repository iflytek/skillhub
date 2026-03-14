package com.iflytek.skillhub.dto;

public record AuthMethodResponse(
    String id,
    String methodType,
    String provider,
    String displayName,
    String actionUrl
) {}
