package com.iflytek.skillhub.domain.event;
public record PromotionRejectedEvent(Long promotionId, Long skillId, String reviewerId, String submitterId, String reason) {}
