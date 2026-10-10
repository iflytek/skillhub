package com.iflytek.skillhub.domain.social.event;

public record SkillRatedEvent(Long skillId, String userId, short score) {}
