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
            transaction.executeWithoutResult(status -> repository.recoverExpired(clock.instant()));
            for (int i = 0; i < properties.getBatchSize() && !stopping.get(); i++) {
                var claimed = transaction.execute(
                        status -> repository.claim(clock.instant(), leaseEnd(), UUID.randomUUID().toString()));
                if (claimed == null || claimed.isEmpty()) {
                    break;
                }
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
            String state = switch (outcome) {
                case ACCEPTED -> "SENT";
                case PERMANENT_FAILURE -> "FAILED";
                case UNKNOWN -> "UNKNOWN";
                case TEMPORARY_FAILURE -> authorized.attempts >= properties.getMaxAttempts() ? "FAILED" : "RETRY";
            };
            String code = outcome == NotificationEmailSender.Result.TEMPORARY_FAILURE && "FAILED".equals(state)
                    ? "RETRY_EXHAUSTED"
                    : outcome.name();
            long backoff = Math.min(3600, 30L << Math.min(authorized.attempts, 10));
            repository.finish(claimed.id(), claimed.leaseToken(), state, code, now.plusSeconds(backoff),
                    outcome == NotificationEmailSender.Result.ACCEPTED ? now : null);
        });
    }

    private Authorized authorize(NotificationEmailDelivery claim) {
        // Shared lock order with settings/contact/preference writes. Batch lock always last.
        var setting = policy.lockCurrent();
        var recipient = recipients.lockCurrent(claim.recipientId()).orElse(null);
        var modes = preferences.lockCurrent(claim.recipientId());
        var batch = repository.lock(claim.id());
        if (batch == null || !"CLAIMED".equals(batch.status())
                || !Objects.equals(batch.leaseToken(), claim.leaseToken())
                || !batch.leaseUntil().isAfter(clock.instant()))
            return null;
        if (!setting.enabled() || recipient == null || recipient.emailRevision() != batch.emailRevision()) {
            repository.suppress(batch.id(), claim.leaseToken(), "RECIPIENT_OR_PLATFORM_UNAVAILABLE");
            return null;
        }
        List<NotificationEmailItem> active = new ArrayList<>();
        for (var item : repository.items(batch.id())) {
            if (!"ACTIVE".equals(item.status())) {
                continue;
            }
            if (modes.getOrDefault(item.category(), NotificationEmailMode.OFF) == NotificationEmailMode.OFF
                    || !eligibility.eligible(item))
                repository.suppressItem(item.id());
            else
                active.add(item);
        }
        if (active.isEmpty()) {
            repository.suppress(batch.id(), claim.leaseToken(), "NO_ELIGIBLE_ITEMS");
            return null;
        }
        if (!repository.authorize(batch.id(), claim.leaseToken(), clock.instant(), leaseEnd())) {
            return null;
        }
        String link = setting.publicBaseUrl().replaceAll("/+$", "") + "/dashboard/notifications/email/" + batch.id();
        boolean zh = "zh".equals(recipient.locale());
        String subject =
                zh ? "SkillHub 通知（" + active.size() + " 条）" : "SkillHub notifications (" + active.size() + ")";
        StringBuilder text =
                new StringBuilder(zh ? "您有以下通知（时间为 UTC）：\n" : "Your notifications (times in UTC):\n");
        var counts = new EnumMap<com.iflytek.skillhub.notification.domain.NotificationCategory, Integer>(
                com.iflytek.skillhub.notification.domain.NotificationCategory.class);
        active.forEach(item -> counts.merge(item.category(), 1, Integer::sum));
        int displayed = 0;
        for (var entry : counts.entrySet()) {
            text.append('\n').append(entry.getKey().name()).append(": ").append(entry.getValue()).append('\n');
            for (var item : active) {
                if (item.category() == entry.getKey() && displayed < 50) {
                    text.append(item.category()).append(" · ").append(item.occurredAt()).append('\n');
                    displayed++;
                }
            }
        }
        if (active.size() > 50) {
            text.append(zh ? "更多通知请查看详情。\n" : "See details for remaining notifications.\n");
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
