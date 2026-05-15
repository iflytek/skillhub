package com.iflytek.skillhub.domain.promotion;

/**
 * Lifecycle states for a promotion campaign.
 */
public enum PromotionCampaignStatus {
    DRAFT,
    PENDING_REVIEW,
    SCHEDULED,
    ACTIVE,
    ENDED,
    REJECTED
}
