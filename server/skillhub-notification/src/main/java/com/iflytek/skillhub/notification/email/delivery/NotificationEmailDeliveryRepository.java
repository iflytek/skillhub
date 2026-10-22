package com.iflytek.skillhub.notification.email.delivery;

import java.time.Instant;
import java.util.*;
public interface NotificationEmailDeliveryRepository {
    Optional<NotificationEmailDelivery> find(long id);
    NotificationEmailDelivery lock(long id);
    NotificationEmailDelivery openBatch(
            String recipientId, String kind, String key, long emailRevision, Instant dueAt, Instant now);
    boolean addItem(long deliveryId, NotificationEmailCommand command, Long skillId, Instant now);
    void suppressItem(long id);
    /** Checks at most one bounded preparation/preview page against durable OFF barriers. */
    Set<Long> cancelledItemIds(List<Long> ids);
    /** Advances one persisted cancellation cursor by at most 100 raw items. */
    int drainCancelledItems();
    Optional<NotificationEmailDelivery> claim(Instant now, Instant until, String token);
    /** Raw immutable group rows in global item-ID order; callers apply current permission checks after LIMIT. */
    List<NotificationEmailItem> itemPage(long deliveryId, long afterId, int limit);
    List<NotificationEmailItem> previewItems(long deliveryId, List<Long> ids);
    Optional<NotificationEmailDelivery> activeRoot(String userId);
    /** Acquire the existing contact gate without waiting; caller must keep the transaction open. */
    boolean lockRecipient(String userId);
    Optional<NotificationEmailDelivery> claim(
            Instant now, Instant until, String token, String preferredKind, Set<String> excludedRecipients);
    NotificationEmailDelivery beginPreparation(long id, String token, Instant now);
    /** Caller holds the contact and root locks; seals at most 100 eligible sources. */
    boolean mergeSourcePage(NotificationEmailDelivery root, Instant now);
    void savePreparation(long id, String token, long cursor, boolean complete, String previewIds, boolean ready);
    void temporaryFailure(long id, String token, Instant now, Instant nextAttempt, int retryWindowHours);
    void expireRecovery(long id, Instant now);
    boolean authorize(long id, String token, Instant now, Instant until);
    boolean finish(long id, String token, String status, String resultCode, Instant nextAttempt, Instant acceptedAt);
    void suppress(long id, String token, String reason, Instant now);
    void recoverExpired(Instant now);
    List<NotificationEmailDelivery> list(String status, int offset, int limit);
    long count(String status);
    void retry(long id, Instant now);
    int cleanup(Instant sentBefore, Instant failedBefore);
}
