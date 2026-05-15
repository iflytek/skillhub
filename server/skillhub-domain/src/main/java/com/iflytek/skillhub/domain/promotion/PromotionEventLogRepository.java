package com.iflytek.skillhub.domain.promotion;

/**
 * Append-only event sink for promotion analytics.
 */
public interface PromotionEventLogRepository {
    PromotionEventLog save(PromotionEventLog event);

    long countByCampaignAndType(Long campaignId, PromotionEventType type);
}
