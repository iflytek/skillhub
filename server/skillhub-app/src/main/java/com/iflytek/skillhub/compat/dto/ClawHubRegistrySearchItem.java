package com.iflytek.skillhub.compat.dto;

public record ClawHubRegistrySearchItem(
        String slug,
        String displayName,
        String summary,
        String version,
        double score,
        long updatedAt
) {}
