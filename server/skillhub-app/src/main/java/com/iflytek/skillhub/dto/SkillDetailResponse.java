package com.iflytek.skillhub.dto;

import java.math.BigDecimal;

public record SkillDetailResponse(
        Long id,
        String slug,
        String displayName,
        String ownerId,
        String ownerDisplayName,
        String summary,
        String visibility,
        String status,
        Long downloadCount,
        Integer starCount,
        BigDecimal ratingAvg,
        Integer ratingCount,
        boolean hidden,
        String namespace,
        boolean canManageLifecycle,
        boolean canSubmitPromotion,
        boolean canInteract,
        boolean canReport,
        SkillLifecycleVersionResponse headlineVersion,
        SkillLifecycleVersionResponse publishedVersion,
        SkillLifecycleVersionResponse ownerPreviewVersion,
        String resolutionMode
) {}
