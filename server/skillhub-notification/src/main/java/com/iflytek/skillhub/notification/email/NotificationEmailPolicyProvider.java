package com.iflytek.skillhub.notification.email;

public interface NotificationEmailPolicyProvider {
    NotificationEmailPolicy current();
    NotificationEmailPolicy lockCurrent();
}
