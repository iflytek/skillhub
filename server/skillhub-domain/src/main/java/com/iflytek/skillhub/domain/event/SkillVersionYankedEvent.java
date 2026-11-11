package com.iflytek.skillhub.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SkillVersionYankedEvent(Long skillId, Long versionId, String actorUserId, boolean wasPublished,
        BusinessEventSnapshot snapshot, String eventId, Instant occurredAt) implements SkillBusinessEvent {
    public SkillVersionYankedEvent(Long skillId, Long versionId, String actorUserId, boolean wasPublished) {
        this(skillId, versionId, actorUserId, wasPublished, (BusinessEventSnapshot) null);
    }

    public SkillVersionYankedEvent(Long skillId, Long versionId, String actorUserId, boolean wasPublished, BusinessEventSnapshot snapshot) {
        this(skillId, versionId, actorUserId, wasPublished, snapshot, UUID.randomUUID().toString(),
                snapshot == null ? Instant.now() : snapshot.occurredAt());
    }

    public SkillVersionYankedEvent(Long skillId, Long versionId, String actorUserId, boolean wasPublished, String eventId, Instant occurredAt) {
        this(skillId, versionId, actorUserId, wasPublished, null, eventId, occurredAt);
    }

    public SkillVersionYankedEvent {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(occurredAt);
    }
}
