package com.iflytek.skillhub.domain.event;

/** Ownership moved to another user without changing the skill's package, versions or address. */
public record SkillOwnerChangedEvent(Long skillId) {}
