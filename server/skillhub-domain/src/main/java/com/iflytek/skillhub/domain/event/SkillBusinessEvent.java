package com.iflytek.skillhub.domain.event;

/** Marker for existing skill facts with a producer-captured scalar analytics snapshot. */
public interface SkillBusinessEvent {
    Long skillId();
    BusinessEventSnapshot snapshot();
}
