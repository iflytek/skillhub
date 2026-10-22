package com.iflytek.skillhub.domain.event;
public record PromotionSubmittedEvent(Long promotionId, Long skillId, Long versionId, String submitterId, String eventId, java.time.Instant occurredAt) {
    public PromotionSubmittedEvent(Long promotionId, Long skillId, Long versionId, String submitterId) {
        this(promotionId,skillId,versionId,submitterId,java.util.UUID.randomUUID().toString(),java.time.Instant.now());
    }
    public PromotionSubmittedEvent {
        java.util.Objects.requireNonNull(eventId);
        java.util.Objects.requireNonNull(occurredAt);
    }
}
