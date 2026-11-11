package com.iflytek.skillhub.domain.event;

public record SkillDownloadedEvent(Long skillId, Long versionId,
                                   BusinessEventSnapshot snapshot) implements SkillBusinessEvent {
    public SkillDownloadedEvent(Long skillId, Long versionId) {
        this(skillId, versionId, null);
    }
}
