package com.iflytek.skillhub.domain.event;

public record SkillVersionYankedEvent(Long skillId, Long versionId, String actorUserId, boolean wasPublished, String eventId, java.time.Instant occurredAt) {
    public SkillVersionYankedEvent(Long skillId, Long versionId, String actorUserId, boolean wasPublished) {
        this(skillId,versionId,actorUserId,wasPublished,java.util.UUID.randomUUID().toString(),java.time.Instant.now());
    }
    public SkillVersionYankedEvent {
        java.util.Objects.requireNonNull(eventId);
        java.util.Objects.requireNonNull(occurredAt);
    }
}
