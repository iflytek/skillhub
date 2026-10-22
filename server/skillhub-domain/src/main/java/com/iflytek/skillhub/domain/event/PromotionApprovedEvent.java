package com.iflytek.skillhub.domain.event;
public record PromotionApprovedEvent(Long promotionId, Long skillId, String reviewerId, String submitterId, String eventId, java.time.Instant occurredAt) {
    public PromotionApprovedEvent(Long promotionId, Long skillId, String reviewerId, String submitterId) {
        this(promotionId,skillId,reviewerId,submitterId,java.util.UUID.randomUUID().toString(),java.time.Instant.now());
    }
    public PromotionApprovedEvent {
        java.util.Objects.requireNonNull(eventId);
        java.util.Objects.requireNonNull(occurredAt);
    }
}
