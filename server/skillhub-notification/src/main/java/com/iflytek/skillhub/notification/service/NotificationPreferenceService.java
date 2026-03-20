package com.iflytek.skillhub.notification.service;

import com.iflytek.skillhub.notification.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;

    public NotificationPreferenceService(NotificationPreferenceRepository preferenceRepository) {
        this.preferenceRepository = preferenceRepository;
    }

    public record PreferenceView(NotificationCategory category, NotificationChannel channel, boolean enabled) {}

    public boolean isEnabled(String userId, NotificationCategory category, NotificationChannel channel) {
        return preferenceRepository.findByUserIdAndCategoryAndChannel(userId, category, channel)
                .map(NotificationPreference::isEnabled)
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public List<PreferenceView> getPreferences(String userId) {
        Map<NotificationCategory, Boolean> saved = preferenceRepository.findByUserId(userId).stream()
                .filter(p -> p.getChannel() == NotificationChannel.IN_APP)
                .collect(Collectors.toMap(NotificationPreference::getCategory, NotificationPreference::isEnabled));
        return Arrays.stream(NotificationCategory.values())
                .map(cat -> new PreferenceView(cat, NotificationChannel.IN_APP, saved.getOrDefault(cat, true)))
                .toList();
    }

    @Transactional
    public void updatePreference(String userId, NotificationCategory category,
                                  NotificationChannel channel, boolean enabled) {
        NotificationPreference pref = preferenceRepository
                .findByUserIdAndCategoryAndChannel(userId, category, channel)
                .orElse(null);
        if (pref == null) {
            pref = new NotificationPreference(userId, category, channel, enabled);
        } else {
            pref.setEnabled(enabled);
        }
        preferenceRepository.save(pref);
    }
}
