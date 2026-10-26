package com.iflytek.skillhub.notification.email;

public interface NotificationEmailPolicyProvider {
    NotificationEmailPolicy current();
    /**
     * Shared row read guard held until the caller's read/write transaction ends.
     * Refreshes after waiting for policy writes; PostgreSQL locking reads require a writable transaction.
     */
    NotificationEmailPolicy lockCurrent();
}
