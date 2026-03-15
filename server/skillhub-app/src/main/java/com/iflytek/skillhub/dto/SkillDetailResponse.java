package com.iflytek.skillhub.dto;

import java.math.BigDecimal;

public record SkillDetailResponse(
        Long id,
        String slug,
        String displayName,
        String summary,
        String visibility,
        String status,
        Long downloadCount,
        Integer starCount,
        BigDecimal ratingAvg,
        Integer ratingCount,
        boolean hidden,
        String latestVersion,
        String namespace,
        boolean canManageLifecycle
) {}
