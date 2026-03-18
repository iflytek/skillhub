package com.iflytek.skillhub.dto;

import java.time.Instant;

public record AdminSkillReportSummaryResponse(
        Long id,
        Long skillId,
        String namespace,
        String skillSlug,
        String skillDisplayName,
        String reporterId,
        String reason,
        String details,
        String status,
        String handledBy,
        String handleComment,
        Instant createdAt,
        Instant handledAt
) {}
