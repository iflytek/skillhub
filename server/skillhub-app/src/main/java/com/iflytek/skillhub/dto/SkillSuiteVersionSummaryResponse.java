package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.skill.SkillVisibility;

import java.time.Instant;

/** Viewer-filtered Suite version history item. */
public record SkillSuiteVersionSummaryResponse(
        Long id,
        String version,
        String status,
        SkillVisibility visibility,
        Instant publishedAt,
        Instant yankedAt,
        Instant createdAt
) {
}
