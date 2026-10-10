package com.iflytek.skillhub.dto;

public record ResolveVersionResponse(
        Long skillId,
        String namespace,
        String slug,
        String version,
        Long versionId,
        String fingerprint,
        Boolean matched,
        String downloadUrl
) {}
