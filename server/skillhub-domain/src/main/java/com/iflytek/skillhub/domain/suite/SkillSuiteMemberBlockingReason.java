package com.iflytek.skillhub.domain.suite;

/** Stable reason codes used to explain why an exact Suite member cannot be installed. */
public enum SkillSuiteMemberBlockingReason {
    DELETED,
    NAMESPACE_ARCHIVED,
    NAMESPACE_FROZEN,
    SKILL_HIDDEN,
    SKILL_ARCHIVED,
    VERSION_UNAVAILABLE,
    VISIBILITY_INCOMPATIBLE
}
