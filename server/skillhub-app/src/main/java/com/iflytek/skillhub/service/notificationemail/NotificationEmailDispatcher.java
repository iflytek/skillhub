package com.iflytek.skillhub.service.notificationemail;

import com.iflytek.skillhub.notification.email.*;
import com.iflytek.skillhub.notification.email.delivery.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class NotificationEmailDispatcher {
    private static final Logger log = LoggerFactory.getLogger(NotificationEmailDispatcher.class);
    private final NotificationEmailDeliveryRepository repository;
    private final NotificationEmailPolicyProvider policy;
    private final NotificationEmailRecipientProvider recipients;
    private final NotificationEmailPreferenceService preferences;
    private final NotificationEmailEligibility eligibility;
    private final NotificationEmailSender sender;
    private final NotificationEmailDeliveryProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final AtomicBoolean dispatchRunning = new AtomicBoolean();
    private final AtomicBoolean cleanupRunning = new AtomicBoolean();
    private final AtomicBoolean immediateFirst = new AtomicBoolean();
    private final AtomicBoolean stopping = new AtomicBoolean();
    public NotificationEmailDispatcher(NotificationEmailDeliveryRepository repository,
            NotificationEmailPolicyProvider policy, NotificationEmailRecipientProvider recipients,
            NotificationEmailPreferenceService preferences, NotificationEmailEligibility eligibility,
            NotificationEmailSender sender, NotificationEmailDeliveryProperties properties, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.policy = policy;
        this.recipients = recipients;
        this.preferences = preferences;
        this.eligibility = eligibility;
        this.sender = sender;
        this.properties = properties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void dispatch() {
        if (stopping.get() || !dispatchRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            transaction.executeWithoutResult(status -> {
                policy.lockCurrent();
                repository.drainCancelledItems();
            });
            transaction.executeWithoutResult(status -> repository.recoverExpired(clock.instant()));
            Set<String> visited = new HashSet<>();
            boolean firstImmediate = immediateFirst.getAndSet(!immediateFirst.get());
            for (int i = 0; i < properties.getBatchSize() && !stopping.get(); i++) {
                String preferred = (i % 2 == 0) == firstImmediate ? "IMMEDIATE" : "DIGEST";
                var claimed = transaction.execute(status
                        -> repository.claim(
                                clock.instant(), leaseEnd(), UUID.randomUUID().toString(), preferred, visited));
                if (claimed == null || claimed.isEmpty()) {
                    String fallback = "IMMEDIATE".equals(preferred) ? "DIGEST" : "IMMEDIATE";
                    claimed = transaction.execute(status
                            -> repository.claim(
                                    clock.instant(), leaseEnd(), UUID.randomUUID().toString(), fallback, visited));
                }
                if (claimed == null || claimed.isEmpty())
                    break;
                visited.add(claimed.get().recipientId());
                process(claimed.get());
            }
        } catch (RuntimeException ex) {
            // Exception messages may contain SQL parameters or SMTP response text. Do not log them.
            log.error("Notification email dispatcher failed: {}", ex.getClass().getSimpleName());
        } finally {
            dispatchRunning.set(false);
        }
    }

    public void process(NotificationEmailDelivery claimed) {
        if ("DIGEST".equals(claimed.kind())
                && (!claimed.recoveryRoot() || !claimed.sourcesComplete() || claimed.previewItemIds().isBlank())) {
            transaction.executeWithoutResult(status -> prepare(claimed));
            return;
        }
        Authorized authorized = transaction.execute(status -> authorize(claimed));
        if (authorized == null) {
            return;
        }
        NotificationEmailSender.Result result;
        try {
            result = sender.send(authorized.recipient.email(), authorized.recipient.locale(), claimed.id(),
                    authorized.subject, authorized.text);
        } catch (RuntimeException ex) {
            result = NotificationEmailSender.Result.UNKNOWN;
        }
        var outcome = result;
        transaction.executeWithoutResult(status -> {
            Instant now = clock.instant();
            repository.recoverExpired(now);
            long backoff = Math.min(3600, 30L << Math.min(authorized.attempts, 10));
            if (outcome == NotificationEmailSender.Result.TEMPORARY_FAILURE) {
                repository.temporaryFailure(claimed.id(), claimed.leaseToken(), now, now.plusSeconds(backoff),
                        properties.getRetryWindowHours());
            } else {
                String state = switch (outcome) {
                    case ACCEPTED -> "SENT";
                    case PERMANENT_FAILURE -> "FAILED";
                    default -> "UNKNOWN";
                };
                repository.finish(claimed.id(), claimed.leaseToken(), state, outcome.name(), now,
                        outcome == NotificationEmailSender.Result.ACCEPTED ? now : null);
            }
        });
    }

    private boolean validClaim(NotificationEmailDelivery batch, NotificationEmailDelivery claim) {
        return batch != null && "CLAIMED".equals(batch.status())
                && Objects.equals(batch.leaseToken(), claim.leaseToken())
                && batch.leaseUntil().isAfter(clock.instant());
    }

    private boolean blockedByRoot(NotificationEmailDelivery batch) {
        var root = repository.activeRoot(batch.recipientId()).orElse(null);
        return "DIGEST".equals(batch.kind()) && root != null && root.id() != batch.id();
    }

    private boolean available(
            NotificationEmailDelivery batch, NotificationEmailRecipient recipient, NotificationEmailPolicy setting) {
        if (batch.retryDeadline() != null && !batch.retryDeadline().isAfter(clock.instant())) {
            repository.expireRecovery(batch.id(), clock.instant());
            return false;
        }
        if (!setting.enabled() || recipient == null || recipient.emailRevision() != batch.emailRevision()) {
            repository.suppress(batch.id(), batch.leaseToken(), "RECIPIENT_OR_PLATFORM_UNAVAILABLE", clock.instant());
            return false;
        }
        return true;
    }

    /** One bounded preparation page per scheduler round. The contact gate also blocks stale claimed sources. */
    private void prepare(NotificationEmailDelivery claim) {
        var setting = policy.lockCurrent();
        var recipient = recipients.lockCurrent(claim.recipientId()).orElse(null);
        if (!repository.lockRecipient(claim.recipientId()))
            return;
        var modes = preferences.lockCurrent(claim.recipientId());
        var batch = repository.lock(claim.id());
        if (!validClaim(batch, claim) || blockedByRoot(batch) || !available(batch, recipient, setting))
            return;
        var root = repository.beginPreparation(batch.id(), claim.leaseToken(), clock.instant());
        if (root == null)
            return;
        if (!root.sourcesComplete()) {
            boolean done = repository.mergeSourcePage(root, clock.instant());
            root = repository.find(root.id()).orElseThrow();
            if (!done) {
                repository.savePreparation(root.id(), claim.leaseToken(), root.itemCursor(), root.itemsComplete(),
                        root.previewItemIds(), false);
                return;
            }
        }
        if (root.retryDeadline() != null && !root.retryDeadline().isAfter(clock.instant())) {
            repository.expireRecovery(root.id(), clock.instant());
            return;
        }
        var raw = repository.itemPage(root.id(), root.itemCursor(), 100);
        var cancelled = repository.cancelledItemIds(raw.stream().map(NotificationEmailItem::id).toList());
        List<Long> previews = new ArrayList<>();
        long cursor = root.itemCursor();
        // Stop once a bounded preview exists. We do not scan an entire backlog to fill 50 slots.
        for (var item : raw) {
            cursor = item.id();
            if ("ACTIVE".equals(item.status()) && !cancelled.contains(item.id())
                    && modes.getOrDefault(item.category(), NotificationEmailMode.OFF) != NotificationEmailMode.OFF
                    && eligibility.eligible(item))
                previews.add(item.id());
            else if ("ACTIVE".equals(item.status()))
                repository.suppressItem(item.id());
            if (previews.size() == 50)
                break;
        }
        boolean complete = raw.size() < 100 && (raw.isEmpty() || cursor == raw.getLast().id());
        if (previews.isEmpty() && complete) {
            repository.suppress(root.id(), claim.leaseToken(), "NO_ELIGIBLE_ITEMS", clock.instant());
            return;
        }
        String ids = previews.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        repository.savePreparation(root.id(), claim.leaseToken(), cursor, complete, ids, !previews.isEmpty());
    }

    private Authorized authorize(NotificationEmailDelivery claim) {
        var setting = policy.lockCurrent();
        var recipient = recipients.lockCurrent(claim.recipientId()).orElse(null);
        if (!repository.lockRecipient(claim.recipientId()))
            return null;
        var modes = preferences.lockCurrent(claim.recipientId());
        var batch = repository.lock(claim.id());
        if (!validClaim(batch, claim) || blockedByRoot(batch) || !available(batch, recipient, setting))
            return null;
        List<NotificationEmailItem> candidates;
        if (batch.recoveryRoot()) {
            var ids = Arrays.stream(batch.previewItemIds().split(","))
                              .filter(value -> !value.isBlank())
                              .map(Long::parseLong)
                              .limit(50)
                              .toList();
            candidates = repository.previewItems(batch.id(), ids);
        } else {
            candidates = repository.itemPage(batch.id(), 0, 50);
        }
        var cancelled = repository.cancelledItemIds(candidates.stream().map(NotificationEmailItem::id).toList());
        List<NotificationEmailItem> active = new ArrayList<>();
        for (var item : candidates) {
            if (!"ACTIVE".equals(item.status()))
                continue;
            if (cancelled.contains(item.id())
                    || modes.getOrDefault(item.category(), NotificationEmailMode.OFF) == NotificationEmailMode.OFF
                    || !eligibility.eligible(item))
                repository.suppressItem(item.id());
            else
                active.add(item);
        }
        if (active.isEmpty()) {
            if (batch.recoveryRoot() && !batch.itemsComplete()) {
                repository.beginPreparation(batch.id(), claim.leaseToken(), clock.instant());
                repository.savePreparation(batch.id(), claim.leaseToken(), batch.itemCursor(), false, "", false);
            } else
                repository.suppress(batch.id(), claim.leaseToken(), "NO_ELIGIBLE_ITEMS", clock.instant());
            return null;
        }
        if (!repository.authorize(batch.id(), claim.leaseToken(), clock.instant(), leaseEnd()))
            return null;
        String link = setting.publicBaseUrl().replaceAll("/+$", "") + "/dashboard/notifications/email/" + batch.id();
        boolean zh = "zh".equals(recipient.locale());
        String subject = zh ? "SkillHub 通知摘要" : "SkillHub notifications";
        StringBuilder text = new StringBuilder(zh ? "原始通知记录数：" : "Original notification records: ");
        text.append(batch.itemCount())
                .append(zh ? "。实际可见内容以详情为准。时间为 UTC。\n"
                           : ". Current access is checked in details. Times are UTC.\n");
        for (var category : com.iflytek.skillhub.notification.domain.NotificationCategory.values()) {
            var section = active.stream().filter(item -> item.category() == category).toList();
            if (section.isEmpty())
                continue;
            String label = switch (category) {
                case PUBLISH -> zh ? "发布动态" : "Publishing";
                case REVIEW -> zh ? "审核进度" : "Reviews";
                case PROMOTION -> zh ? "全局提升" : "Promotions";
                case REPORT -> zh ? "举报处理" : "Reports";
            };
            text.append('\n').append(label).append(zh ? "（预览）\n" : " (preview)\n");
            for (var item : section) text.append(label).append(" · ").append(item.occurredAt()).append('\n');
        }
        text.append('\n').append(link).append('\n');
        return new Authorized(recipient, batch.attempts() + 1, subject, text.toString());
    }

    public void cleanup() {
        if (stopping.get() || !cleanupRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            transaction.executeWithoutResult(status
                    -> repository.cleanup(clock.instant().minus(Duration.ofDays(properties.getSentRetentionDays())),
                            clock.instant().minus(Duration.ofDays(properties.getFailedRetentionDays()))));
        } finally {
            cleanupRunning.set(false);
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void stop() {
        stopping.set(true);
    }

    private Instant leaseEnd() {
        return clock.instant().plusSeconds(properties.getLeaseSeconds());
    }

    private record Authorized(NotificationEmailRecipient recipient, int attempts, String subject, String text) {}
}
