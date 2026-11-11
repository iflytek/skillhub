package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;

import java.util.Set;

/** Capability ceilings for cross-entry-point Skill state transitions; null denotes a session. */
public final class SkillTokenCapabilityPolicy {

    public static final String MANAGE = "skill:manage";
    public static final String PUBLISH_PRIVATE = "skill:publish-private";

    private SkillTokenCapabilityPolicy() {
    }

    public static void require(Set<String> capabilities, String capability) {
        if (capabilities != null && !capabilities.contains(capability)) {
            throw new DomainForbiddenException("error.apiToken.scope.missing", capability);
        }
    }
}
