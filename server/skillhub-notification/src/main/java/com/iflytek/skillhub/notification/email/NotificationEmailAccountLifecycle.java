package com.iflytek.skillhub.notification.email;

public interface NotificationEmailAccountLifecycle {
    /** Caller holds the account lock and changes a non-active account to active in the same transaction. */
    void markActive(String userId);
}
