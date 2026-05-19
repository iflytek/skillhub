package com.iflytek.skillhub.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record SkillSummaryResponse(
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
        String namespace,
        Instant updatedAt,
        boolean canSubmitPromotion,
        SkillLifecycleVersionResponse headlineVersion,
        SkillLifecycleVersionResponse publishedVersion,
        SkillLifecycleVersionResponse ownerPreviewVersion,
        String resolutionMode
) {}
