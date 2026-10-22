package com.iflytek.skillhub.notification.email;

import com.iflytek.skillhub.notification.domain.NotificationCategory;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class NotificationEmailPreferenceService {
    private final NotificationEmailPreferenceRepository repository;
    public NotificationEmailPreferenceService(NotificationEmailPreferenceRepository repository) {
        this.repository = repository;
    }
    public Map<NotificationCategory, NotificationEmailMode> current(String userId) {
        return modes(repository.findByUserIdOrderByCategoryAsc(userId));
    }
    /** Caller must hold settings, account and contact locks before calling this method. */
    public Map<NotificationCategory, NotificationEmailMode> lockCurrent(String userId) {
        return modes(repository.lockAll(userId));
    }
    private Map<NotificationCategory, NotificationEmailMode> modes(List<NotificationEmailPreference> rows) {
        Map<NotificationCategory, NotificationEmailMode> result = new EnumMap<>(NotificationCategory.class);
        for (var category : NotificationCategory.values()) result.put(category, NotificationEmailMode.OFF);
        rows.forEach(row -> result.put(row.getCategory(), row.getMode()));
        return result;
    }
}
