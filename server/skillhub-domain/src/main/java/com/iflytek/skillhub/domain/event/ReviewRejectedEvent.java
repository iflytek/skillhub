package com.iflytek.skillhub.domain.event;
public record ReviewRejectedEvent(Long reviewId, Long skillId, Long versionId, String reviewerId, String submitterId, String reason, String eventId, java.time.Instant occurredAt) {
    public ReviewRejectedEvent(Long reviewId, Long skillId, Long versionId, String reviewerId, String submitterId, String reason) {
        this(reviewId,skillId,versionId,reviewerId,submitterId,reason,java.util.UUID.randomUUID().toString(),java.time.Instant.now());
    }
    public ReviewRejectedEvent {
        java.util.Objects.requireNonNull(eventId);
        java.util.Objects.requireNonNull(occurredAt);
    }
}
