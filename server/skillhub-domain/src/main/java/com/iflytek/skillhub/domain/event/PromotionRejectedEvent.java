package com.iflytek.skillhub.domain.event;
public record PromotionRejectedEvent(Long promotionId, Long skillId, String reviewerId, String submitterId, String reason, String eventId, java.time.Instant occurredAt) {
    public PromotionRejectedEvent(Long promotionId, Long skillId, String reviewerId, String submitterId, String reason) {
        this(promotionId,skillId,reviewerId,submitterId,reason,java.util.UUID.randomUUID().toString(),java.time.Instant.now());
    }
    public PromotionRejectedEvent {
        java.util.Objects.requireNonNull(eventId);
        java.util.Objects.requireNonNull(occurredAt);
    }
}
