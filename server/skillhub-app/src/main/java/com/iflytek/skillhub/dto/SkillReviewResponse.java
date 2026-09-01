package com.iflytek.skillhub.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
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
