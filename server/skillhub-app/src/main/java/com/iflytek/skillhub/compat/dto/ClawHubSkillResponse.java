package com.iflytek.skillhub.compat.dto;

import java.time.ZoneOffset;

public record ClawHubSkillResponse(
    SkillInfo skill,
    VersionInfo latestVersion,
    OwnerInfo owner,
    ModerationInfo moderation
) {
    public record SkillInfo(
        String slug,
        String displayName,
        String summary,
        Object tags,
        Object stats,
        long createdAt,
        long updatedAt
    ) {}

    public record VersionInfo(
        String version,
        long createdAt,
        String changelog,
        String license
    ) {}

    public record OwnerInfo(
        String handle,
        String displayName,
        String image
    ) {}

    public record ModerationInfo(
        boolean isSuspicious,
        boolean isMalwareBlocked,
        String verdict,
        String[] reasonCodes,
        Long updatedAt,
        String engineVersion,
        String summary
    ) {}
}
