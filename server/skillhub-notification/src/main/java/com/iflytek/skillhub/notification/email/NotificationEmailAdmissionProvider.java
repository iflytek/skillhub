package com.iflytek.skillhub.notification.email;

import com.iflytek.skillhub.notification.domain.NotificationCategory;
import java.time.Instant;
import java.util.Optional;

public interface NotificationEmailAdmissionProvider {
    /** Caller holds policy, account/contact and category preference guards, in that order.
     * Returns the earliest event time eligible for admission, or empty if any proof is missing.
     * Does not acquire additional locks. This gate does not apply to already admitted deliveries.
     */
    Optional<Instant> currentUnderLocks(String userId, NotificationCategory category);
}
