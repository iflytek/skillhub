package com.iflytek.skillhub.dto;

import java.time.LocalDateTime;

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
        LocalDateTime createdAt,
        LocalDateTime handledAt
) {}
