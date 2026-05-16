package com.iflytek.skillhub.dto.promotion;

import com.iflytek.skillhub.domain.promotion.PromotionCampaign;
import com.iflytek.skillhub.domain.promotion.PromotionCampaignStatus;
import com.iflytek.skillhub.domain.promotion.PromotionTargetType;

import java.time.Instant;

/**
 * Outbound representation of a {@link PromotionCampaign}.
 */
public record PromotionCampaignResponse(
        Long id,
        PromotionTargetType targetType,
        Long targetId,
        Long targetVersionId,
        String slotCode,
        String title,
        String subtitle,
        Long coverMediaId,
        Long demoMediaId,
        int priority,
        PromotionCampaignStatus status,
        Instant startsAt,
        Instant endsAt,
        String submittedBy,
        String reviewedBy,
        String reviewComment,
        String reason,
        Instant createdAt,
        Instant updatedAt
) {
    public static PromotionCampaignResponse from(PromotionCampaign c) {
        return new PromotionCampaignResponse(
                c.getId(), c.getTargetType(), c.getTargetId(), c.getTargetVersionId(),
                c.getSlotCode(), c.getTitle(), c.getSubtitle(),
                c.getCoverMediaId(), c.getDemoMediaId(),
                c.getPriority(), c.getStatus(),
                c.getStartsAt(), c.getEndsAt(),
                c.getSubmittedBy(), c.getReviewedBy(), c.getReviewComment(),
                c.getReason(), c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}
