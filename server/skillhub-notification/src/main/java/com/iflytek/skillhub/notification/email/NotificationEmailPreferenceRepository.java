package com.iflytek.skillhub.notification.email;

import java.util.List;
public interface NotificationEmailPreferenceRepository {
    List<NotificationEmailPreference> findByUserIdOrderByCategoryAsc(String userId);
    List<NotificationEmailPreference> lockAll(String userId);
    NotificationEmailPreference save(NotificationEmailPreference preference);
}
