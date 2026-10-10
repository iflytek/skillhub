package com.iflytek.skillhub.domain.user;

import java.util.Map;

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

    /** Some fields applied immediately, others queued for review. */
    record Mixed(Map<String, String> appliedFields, Map<String, String> pendingFields)
            implements UpdateProfileResult {}

    /** Convenience factory for the applied case. */
    static UpdateProfileResult applied() {
        return new Applied();
    }

    /** Convenience factory for the pending-review case. */
    static UpdateProfileResult pendingReview() {
        return new PendingReview();
    }

    /** Convenience factory for the mixed case. */
    static UpdateProfileResult mixed(Map<String, String> appliedFields, Map<String, String> pendingFields) {
        return new Mixed(appliedFields, pendingFields);
    }
}
