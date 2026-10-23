package com.iflytek.skillhub.notification.email;

import java.util.Optional;
public interface NotificationEmailContactRepository {
    Optional<NotificationEmailContact> findById(String userId);
    NotificationEmailContact lockOrCreate(String userId);
    NotificationEmailContact save(NotificationEmailContact contact);
}
