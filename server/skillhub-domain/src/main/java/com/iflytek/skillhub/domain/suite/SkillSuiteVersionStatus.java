package com.iflytek.skillhub.domain.suite;

/**
 * Lifecycle states for Suite definitions. Suite versions never enter package scanning states.
 */
public enum SkillSuiteVersionStatus {
    DRAFT,
    PENDING_REVIEW,
    PUBLISHED,
    REJECTED,
    YANKED
}
