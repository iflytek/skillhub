package com.iflytek.skillhub.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SkillPublishedEvent(Long skillId, Long versionId, String publisherId,
        BusinessEventSnapshot snapshot, String eventId, Instant occurredAt) implements SkillBusinessEvent {
    public SkillPublishedEvent(Long skillId, Long versionId, String publisherId) {
        this(skillId, versionId, publisherId, (BusinessEventSnapshot) null);
    }

    public SkillPublishedEvent(Long skillId, Long versionId, String publisherId, BusinessEventSnapshot snapshot) {
        this(skillId, versionId, publisherId, snapshot, UUID.randomUUID().toString(),
                snapshot == null ? Instant.now() : snapshot.occurredAt());
    }

    public SkillPublishedEvent(Long skillId, Long versionId, String publisherId, String eventId, Instant occurredAt) {
        this(skillId, versionId, publisherId, null, eventId, occurredAt);
    }

    public SkillPublishedEvent {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(occurredAt);
    }
}
