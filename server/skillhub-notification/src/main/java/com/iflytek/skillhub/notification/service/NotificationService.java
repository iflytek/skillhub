package com.iflytek.skillhub.notification.service;

import com.iflytek.skillhub.notification.domain.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public NotificationService(NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Transactional
    public Notification create(String recipientId, NotificationCategory category,
                                String eventType, String title, String bodyJson,
                                String entityType, Long entityId) {
        Notification notification = new Notification(recipientId, category, eventType,
                title, bodyJson, entityType, entityId, Instant.now(clock));
        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(String recipientId, NotificationCategory category, Pageable pageable) {
        if (category != null) {
            return notificationRepository.findByRecipientIdAndCategory(recipientId, category, pageable);
        }
        return notificationRepository.findByRecipientId(recipientId, pageable);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String recipientId) {
        return notificationRepository.countByRecipientIdAndStatus(recipientId, NotificationStatus.UNREAD);
    }

    @Transactional
    public void markRead(Long notificationId, String userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
        if (!notification.getRecipientId().equals(userId)) {
            throw new IllegalArgumentException("Not authorized to mark this notification as read");
        }
        notification.markRead(Instant.now(clock));
        notificationRepository.save(notification);
    }

    @Transactional
    public int markAllRead(String userId) {
        return notificationRepository.markAllReadByRecipientId(userId, Instant.now(clock));
    }
}
