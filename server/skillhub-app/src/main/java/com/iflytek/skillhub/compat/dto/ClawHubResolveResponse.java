package com.iflytek.skillhub.compat.dto;

public record ClawHubResolveResponse(
    VersionInfo match,
    VersionInfo latestVersion
) {
    public record VersionInfo(
        String version
    ) {}
}
