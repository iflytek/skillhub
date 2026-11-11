package com.iflytek.skillhub.notification.email.delivery;

import com.iflytek.skillhub.notification.domain.NotificationCategory;
import java.time.Instant;
public record NotificationEmailItem(long id, long deliveryId, String recipientId, String eventKey,
        NotificationCategory category, String eventType, Long skillId, Long entityId, Instant occurredAt,
        Instant enqueuedAt, String status) {}
