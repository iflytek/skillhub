package com.iflytek.skillhub.notification.email.delivery;

import com.iflytek.skillhub.notification.domain.NotificationCategory;
import java.time.Instant;
/** bodyJson is transient input for whitelisted numeric references only; never stored in email tables. */
public record NotificationEmailCommand(String recipientId, NotificationCategory category, String eventType,
        String eventKey, Instant occurredAt, String bodyJson, String entityType, Long entityId) {}
