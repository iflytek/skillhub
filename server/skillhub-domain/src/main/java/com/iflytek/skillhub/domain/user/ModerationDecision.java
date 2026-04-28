package com.iflytek.skillhub.domain.user;

/**
 * Outcome of a profile moderation check.
 *
 * <p>Callers should handle all cases explicitly.
 */
public enum ModerationDecision {
    /** Change is approved — apply immediately. */
    APPROVED,
    /** Change is rejected — return error to user. */
    REJECTED,
    /** Change needs human review — queue for reviewer. */
    NEEDS_REVIEW
}
