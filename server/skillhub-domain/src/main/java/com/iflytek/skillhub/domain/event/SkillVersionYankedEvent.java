package com.iflytek.skillhub.domain.event;

public record SkillVersionYankedEvent(Long skillId, Long versionId, String actorUserId, boolean wasPublished,
                                     BusinessEventSnapshot snapshot) implements SkillBusinessEvent {
    public SkillVersionYankedEvent(Long skillId, Long versionId, String actorUserId, boolean wasPublished) {
        this(skillId, versionId, actorUserId, wasPublished, null);
    }
}
