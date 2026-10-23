package com.iflytek.skillhub.notification.email.delivery;

import java.time.Instant;

public record NotificationEmailDelivery(long id, String recipientId, String kind, String windowKey, long emailRevision,
        String status, int attempts, Instant dueAt, String leaseToken, Instant leaseUntil, String resultCode,
        Instant createdAt, Instant acceptedAt, boolean recoveryRoot, Long mergedIntoDeliveryId,
        Instant recoveryCutoffAt, long sourceHighWatermark, long sourceCursor, boolean sourcesComplete, long itemCursor,
        boolean itemsComplete, String previewItemIds, long itemCount, Instant retryStartedAt, Instant retryDeadline) {
    public NotificationEmailDelivery(long id, String recipientId, String kind, String windowKey, long emailRevision,
            String status, int attempts, Instant dueAt, String leaseToken, Instant leaseUntil, String resultCode,
            Instant createdAt, Instant acceptedAt) {
        this(id, recipientId, kind, windowKey, emailRevision, status, attempts, dueAt, leaseToken, leaseUntil,
                resultCode, createdAt, acceptedAt, false, null, null, 0, 0, false, 0, false, "", 0, null, null);
    }
}
