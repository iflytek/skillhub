package com.iflytek.skillhub.notification.email.delivery;

import java.time.Instant;
import java.util.*;
public interface NotificationEmailDeliveryRepository {
    Optional<NotificationEmailDelivery> find(long id);
    NotificationEmailDelivery lock(long id);
    NotificationEmailDelivery openBatch(
            String recipientId, String kind, String key, long emailRevision, Instant dueAt, Instant now);
    boolean addItem(long deliveryId, NotificationEmailCommand command, Long skillId, Instant now);
    List<NotificationEmailItem> items(long deliveryId);
    void suppressItem(long id);
    Optional<NotificationEmailDelivery> claim(Instant now, Instant until, String token);
    boolean authorize(long id, String token, Instant now, Instant until);
    boolean finish(long id, String token, String status, String resultCode, Instant nextAttempt, Instant acceptedAt);
    void suppress(long id, String token, String reason);
    void recoverExpired(Instant now);
    List<NotificationEmailDelivery> list(String status, int offset, int limit);
    long count(String status);
    void retry(long id, Instant now);
    int cleanup(Instant sentBefore, Instant failedBefore);
}
