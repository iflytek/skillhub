package com.iflytek.skillhub.compat.dto;

public record ClawHubResolveResponse(
    String canonicalSlug,
    String version,
    String downloadUrl
) {}
