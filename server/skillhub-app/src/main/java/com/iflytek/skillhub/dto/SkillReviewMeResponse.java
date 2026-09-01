package com.iflytek.skillhub.dto;

import java.time.Instant;

public record SkillReviewMeResponse(
        boolean rated,
        short score,
        boolean reviewed,
        Long reviewId,
        String reviewText,
        String status,
        String moderationReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static SkillReviewMeResponse empty() {
        return new SkillReviewMeResponse(false, (short) 0, false, null, null, null, null, null, null);
    }
}
