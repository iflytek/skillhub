package com.iflytek.skillhub.compat.dto;

public record ClawHubRegistrySkillVersion(
        String version,
        long createdAt,
        String changelog,
        Object license
) {}
