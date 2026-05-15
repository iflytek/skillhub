package com.iflytek.skillhub.dto.promotion;

import com.iflytek.skillhub.domain.promotion.PromotionCampaign;
import com.iflytek.skillhub.domain.promotion.PromotionTargetType;

/**
 * Public-facing item rendered into a promotion slot. Excludes audit fields and
 * version sequence to avoid leaking review state to anonymous readers.
 */
public record PromotionSlotItemResponse(
        Long campaignId,
        String slotCode,
        PromotionTargetType targetType,
        Long targetId,
        String title,
        String subtitle,
        String coverUrl,
        String demoGifUrl,
        String targetUrl
) {
    public static PromotionSlotItemResponse from(PromotionCampaign c, String targetUrl) {
        String coverUrl = c.getCoverMediaId() == null ? null : "/api/v1/media/" + c.getCoverMediaId();
        String demoUrl = c.getDemoMediaId() == null ? null : "/api/v1/media/" + c.getDemoMediaId();
        return new PromotionSlotItemResponse(
                c.getId(), c.getSlotCode(), c.getTargetType(), c.getTargetId(),
                c.getTitle(), c.getSubtitle(),
                coverUrl, demoUrl,
                targetUrl
        );
    }
}
