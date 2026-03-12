package com.iflytek.skillhub.compat.dto;

public record ClawHubSkillItem(
    String canonicalSlug,
    String description,
    String latestVersion,
    int starCount
) {}
