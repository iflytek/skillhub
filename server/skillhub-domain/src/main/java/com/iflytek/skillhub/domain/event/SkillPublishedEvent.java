package com.iflytek.skillhub.domain.event;

public record SkillPublishedEvent(Long skillId, Long versionId, String publisherId, String eventId, java.time.Instant occurredAt) {
    public SkillPublishedEvent(Long skillId, Long versionId, String publisherId) {
        this(skillId,versionId,publisherId,java.util.UUID.randomUUID().toString(),java.time.Instant.now());
    }
    public SkillPublishedEvent {
        java.util.Objects.requireNonNull(eventId);
        java.util.Objects.requireNonNull(occurredAt);
    }
}
