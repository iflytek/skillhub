package com.iflytek.skillhub.notification.email.delivery;

import java.time.Instant;
public record NotificationEmailDelivery(long id, String recipientId, String kind, String windowKey, long emailRevision,
        String status, int attempts, Instant dueAt, String leaseToken, Instant leaseUntil, String resultCode,
        Instant createdAt, Instant acceptedAt) {}
