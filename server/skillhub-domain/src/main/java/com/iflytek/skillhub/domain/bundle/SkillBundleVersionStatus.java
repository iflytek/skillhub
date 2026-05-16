package com.iflytek.skillhub.domain.bundle;

/**
 * Lifecycle state of a {@code skill_bundle_version}. Mirrors the skill version
 * state machine but lives in its own enum so the bundle aggregate can evolve
 * independently of {@link com.iflytek.skillhub.domain.skill.SkillVersionStatus}.
 */
public enum SkillBundleVersionStatus {
    DRAFT,
    PENDING_REVIEW,
    PUBLISHED,
    REJECTED,
    YANKED
}
