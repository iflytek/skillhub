package com.iflytek.skillhub.domain.event;
public record ReviewSubmittedEvent(Long reviewId, Long skillId, Long versionId, String submitterId, Long namespaceId, String eventId, java.time.Instant occurredAt) {
    public ReviewSubmittedEvent(Long reviewId, Long skillId, Long versionId, String submitterId, Long namespaceId) {
        this(reviewId,skillId,versionId,submitterId,namespaceId,java.util.UUID.randomUUID().toString(),java.time.Instant.now());
    }
    public ReviewSubmittedEvent {
        java.util.Objects.requireNonNull(eventId);
        java.util.Objects.requireNonNull(occurredAt);
    }
}
