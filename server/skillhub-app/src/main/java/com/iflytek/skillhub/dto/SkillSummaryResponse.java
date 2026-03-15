package com.iflytek.skillhub.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SkillSummaryResponse(
        Long id,
        String slug,
        String displayName,
        String summary,
        Long downloadCount,
        Integer starCount,
        BigDecimal ratingAvg,
        Integer ratingCount,
        String latestVersion,
        String latestVersionStatus,
        String namespace,
        LocalDateTime updatedAt
) {}
