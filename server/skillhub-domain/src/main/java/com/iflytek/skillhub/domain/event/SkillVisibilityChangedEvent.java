package com.iflytek.skillhub.domain.event;

import com.iflytek.skillhub.domain.skill.SkillVisibility;

/** Access changed without publishing a new package version. */
public record SkillVisibilityChangedEvent(Long skillId, SkillVisibility previousVisibility,
        SkillVisibility visibility, BusinessEventSnapshot snapshot) implements SkillBusinessEvent {
    public SkillVisibilityChangedEvent(Long skillId, SkillVisibility previousVisibility, SkillVisibility visibility) {
        this(skillId, previousVisibility, visibility, null);
    }
}
