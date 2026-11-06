package com.iflytek.skillhub.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReviewApprovedEvent(Long reviewId, Long skillId, Long versionId, String reviewerId, String submitterId,
        BusinessEventSnapshot snapshot, String eventId, Instant occurredAt) implements SkillBusinessEvent {
    public ReviewApprovedEvent(Long reviewId, Long skillId, Long versionId, String reviewerId, String submitterId) {
        this(reviewId, skillId, versionId, reviewerId, submitterId, (BusinessEventSnapshot) null);
    }

    public ReviewApprovedEvent(Long reviewId, Long skillId, Long versionId, String reviewerId, String submitterId, BusinessEventSnapshot snapshot) {
        this(reviewId, skillId, versionId, reviewerId, submitterId, snapshot, UUID.randomUUID().toString(),
                snapshot == null ? Instant.now() : snapshot.occurredAt());
    }

    public ReviewApprovedEvent(Long reviewId, Long skillId, Long versionId, String reviewerId, String submitterId, String eventId, Instant occurredAt) {
        this(reviewId, skillId, versionId, reviewerId, submitterId, null, eventId, occurredAt);
    }

    public ReviewApprovedEvent {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(occurredAt);
    }
}
