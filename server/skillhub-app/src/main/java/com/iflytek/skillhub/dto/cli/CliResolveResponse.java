package com.iflytek.skillhub.dto.cli;

public record CliResolveResponse(
        String namespace,
        String slug,
        String version,
        Long versionId,
        String fingerprint,
        String downloadUrl
) {}
