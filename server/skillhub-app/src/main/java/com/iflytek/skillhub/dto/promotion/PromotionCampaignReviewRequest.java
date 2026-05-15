package com.iflytek.skillhub.dto.promotion;

import jakarta.validation.constraints.Size;

/**
 * Common payload for review actions (approve / reject).
 */
public record PromotionCampaignReviewRequest(
        @Size(max = 1000) String comment
) {}
