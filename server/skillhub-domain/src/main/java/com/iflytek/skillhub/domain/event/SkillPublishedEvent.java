package com.iflytek.skillhub.domain.event;

public record SkillPublishedEvent(Long skillId, Long versionId, String publisherId,
                                  BusinessEventSnapshot snapshot) implements SkillBusinessEvent {
    public SkillPublishedEvent(Long skillId, Long versionId, String publisherId) {
        this(skillId, versionId, publisherId, null);
    }
}
