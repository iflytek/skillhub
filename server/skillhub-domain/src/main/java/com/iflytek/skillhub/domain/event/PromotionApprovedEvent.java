package com.iflytek.skillhub.domain.event;
public record PromotionApprovedEvent(Long promotionId, Long skillId, String reviewerId, String submitterId) {}
