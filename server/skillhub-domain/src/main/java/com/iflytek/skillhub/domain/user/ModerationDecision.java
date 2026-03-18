package com.iflytek.skillhub.domain.user;

/**
 * Outcome of a profile moderation check.
 *
 * <p>Used as a sealed hierarchy so callers must handle all cases
 * via pattern matching (Java 21 switch expressions).
 */
public enum ModerationDecision {
    /** Change is approved — apply immediately. */
    APPROVED,
    /** Change is rejected — return error to user. */
    REJECTED,
    /** Change needs human review — queue for reviewer. */
    NEEDS_REVIEW
}
