package com.iflytek.skillhub.domain.user;

/**
 * Result of a profile update operation.
 *
 * <p>Uses a sealed interface with record implementations (Java 17+)
 * to enable exhaustive pattern matching in callers.
 *
 * @see UserProfileService#updateProfile
 */
public sealed interface UpdateProfileResult {

    /** Changes were applied immediately to user_account. */
    record Applied() implements UpdateProfileResult {}

    /** Changes are queued for human review (not yet applied). */
    record PendingReview() implements UpdateProfileResult {}

    /** Convenience factory for the applied case. */
    static UpdateProfileResult applied() {
        return new Applied();
    }

    /** Convenience factory for the pending-review case. */
    static UpdateProfileResult pendingReview() {
        return new PendingReview();
    }
}
