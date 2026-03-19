package com.iflytek.skillhub.notification.service;

import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.domain.NotificationChannel;
import com.iflytek.skillhub.notification.domain.Notification;
import com.iflytek.skillhub.notification.sse.SseEmitterManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationService notificationService;
    private final NotificationPreferenceService preferenceService;
    private final SseEmitterManager sseEmitterManager;

    public NotificationDispatcher(NotificationService notificationService,
                                   NotificationPreferenceService preferenceService,
                                   SseEmitterManager sseEmitterManager) {
        this.notificationService = notificationService;
        this.preferenceService = preferenceService;
        this.sseEmitterManager = sseEmitterManager;
    }

    public void dispatch(String recipientId, NotificationCategory category,
                          String eventType, String title, String bodyJson,
                          String entityType, Long entityId) {
        // Check user preference
        if (!preferenceService.isEnabled(recipientId, category, NotificationChannel.IN_APP)) {
            log.debug("Notification {} suppressed for user {} (preference disabled)", eventType, recipientId);
            return;
        }

        // Persist notification
        Notification notification = notificationService.create(
                recipientId, category, eventType, title, bodyJson, entityType, entityId);

        // Push via SSE
        try {
            sseEmitterManager.push(recipientId, Map.of(
                    "id", notification.getId(),
                    "category", notification.getCategory().name(),
                    "eventType", notification.getEventType(),
                    "title", notification.getTitle(),
                    "bodyJson", notification.getBodyJson() != null ? notification.getBodyJson() : "",
                    "entityType", notification.getEntityType() != null ? notification.getEntityType() : "",
                    "entityId", notification.getEntityId() != null ? notification.getEntityId() : 0,
                    "createdAt", notification.getCreatedAt().toString()
            ));
        } catch (Exception e) {
            log.warn("Failed to push SSE notification to user {}", recipientId, e);
            // Notification is already persisted, SSE push failure is non-critical
        }
    }
}
