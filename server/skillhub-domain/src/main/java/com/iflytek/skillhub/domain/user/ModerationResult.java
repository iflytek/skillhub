package com.iflytek.skillhub.domain.user;

/**
 * Result of a profile moderation check, combining the decision with an optional reason.
 *
 * @param decision the moderation outcome
 * @param reason   human-readable reason (populated on REJECTED; null otherwise)
 */
public record ModerationResult(ModerationDecision decision, String reason) {

    /** Convenience factory — change approved, no reason needed. */
    public static ModerationResult approved() {
        return new ModerationResult(ModerationDecision.APPROVED, null);
    }

    /** Convenience factory — change rejected with a reason. */
    public static ModerationResult rejected(String reason) {
        return new ModerationResult(ModerationDecision.REJECTED, reason);
    }

    /** Convenience factory — change needs human review. */
    public static ModerationResult needsReview() {
        return new ModerationResult(ModerationDecision.NEEDS_REVIEW, null);
    }
}
