package com.iflytek.skillhub.notification.email.delivery;
public interface NotificationEmailEnqueuer {
    void enqueue(NotificationEmailCommand command);
}
