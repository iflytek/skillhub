package com.iflytek.skillhub.dto;

import java.time.Instant;

public record SkillReviewResponse(
        Long id,
        String userId,
        String displayName,
        String avatarUrl,
        short score,
        String reviewText,
        String status,
        boolean authoredByViewer,
        String moderationReason,
        Instant createdAt,
        Instant updatedAt
) {}
