package com.iflytek.skillhub.domain.event;
public record ReviewApprovedEvent(Long reviewId, Long skillId, Long versionId, String reviewerId, String submitterId, String eventId, java.time.Instant occurredAt) {
    public ReviewApprovedEvent(Long reviewId, Long skillId, Long versionId, String reviewerId, String submitterId) {
        this(reviewId,skillId,versionId,reviewerId,submitterId,java.util.UUID.randomUUID().toString(),java.time.Instant.now());
    }
    public ReviewApprovedEvent {
        java.util.Objects.requireNonNull(eventId);
        java.util.Objects.requireNonNull(occurredAt);
    }
}
