package com.iflytek.skillhub.domain.event;
public record PromotionSubmittedEvent(Long promotionId, Long skillId, Long versionId, String submitterId) {}
