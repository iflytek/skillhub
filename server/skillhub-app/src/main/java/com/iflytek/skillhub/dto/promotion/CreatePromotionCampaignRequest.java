package com.iflytek.skillhub.dto.promotion;

import com.iflytek.skillhub.domain.promotion.PromotionTargetType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Payload to create or submit a new promotion campaign.
 */
public record CreatePromotionCampaignRequest(
        @NotNull PromotionTargetType targetType,
        @NotNull Long targetId,
        Long targetVersionId,
        @NotBlank @Size(max = 64) String slotCode,
        @NotBlank @Size(max = 128) String title,
        @Size(max = 512) String subtitle,
        Long coverMediaId,
        Long demoMediaId,
        @Min(0) @Max(100) int priority,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Size(max = 1000) String reason
) {}
