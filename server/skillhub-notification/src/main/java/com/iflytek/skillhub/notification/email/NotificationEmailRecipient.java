package com.iflytek.skillhub.notification.email;

import java.time.Instant;

public record NotificationEmailRecipient(String userId, String email, long emailRevision, String locale,
        Instant eligibleSince) {
    public NotificationEmailRecipient(String userId, String email, long emailRevision, String locale) {
        this(userId, email, emailRevision, locale, null);
    }
}
