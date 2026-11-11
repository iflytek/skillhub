package com.iflytek.skillhub.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReviewRejectedEvent(Long reviewId, Long skillId, Long versionId, String reviewerId, String submitterId, String reason,
        BusinessEventSnapshot snapshot, String eventId, Instant occurredAt) implements SkillBusinessEvent {
    public ReviewRejectedEvent(Long reviewId, Long skillId, Long versionId, String reviewerId, String submitterId, String reason) {
        this(reviewId, skillId, versionId, reviewerId, submitterId, reason, (BusinessEventSnapshot) null);
    }

    public ReviewRejectedEvent(Long reviewId, Long skillId, Long versionId, String reviewerId, String submitterId, String reason, BusinessEventSnapshot snapshot) {
        this(reviewId, skillId, versionId, reviewerId, submitterId, reason, snapshot, UUID.randomUUID().toString(),
                snapshot == null ? Instant.now() : snapshot.occurredAt());
    }

    public ReviewRejectedEvent(Long reviewId, Long skillId, Long versionId, String reviewerId, String submitterId, String reason, String eventId, Instant occurredAt) {
        this(reviewId, skillId, versionId, reviewerId, submitterId, reason, null, eventId, occurredAt);
    }

    public ReviewRejectedEvent {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(occurredAt);
    }
}
