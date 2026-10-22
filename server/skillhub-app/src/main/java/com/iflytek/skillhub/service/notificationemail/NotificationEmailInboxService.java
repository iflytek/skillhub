package com.iflytek.skillhub.service.notificationemail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.notification.email.*;
import com.iflytek.skillhub.notification.email.delivery.*;
import java.time.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationEmailInboxService implements NotificationEmailEnqueuer {
    private static final Logger log = LoggerFactory.getLogger(NotificationEmailInboxService.class);
    private final NotificationEmailPolicyProvider policy;
    private final NotificationEmailRecipientProvider recipients;
    private final NotificationEmailPreferenceService preferences;
    private final NotificationEmailDeliveryRepository repository;
    private final NotificationEmailEligibility eligibility;
    private final ObjectMapper mapper;
    private final Clock clock;
    public NotificationEmailInboxService(NotificationEmailPolicyProvider policy,
            NotificationEmailRecipientProvider recipients, NotificationEmailPreferenceService preferences,
            NotificationEmailDeliveryRepository repository, NotificationEmailEligibility eligibility,
            ObjectMapper mapper, Clock clock) {
        this.policy = policy;
        this.recipients = recipients;
        this.preferences = preferences;
        this.repository = repository;
        this.eligibility = eligibility;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(NotificationEmailCommand command) {
        Instant now = clock.instant();
        if (command.eventKey() == null || command.eventKey().length() > 160 || command.occurredAt() == null
                || command.occurredAt().isBefore(now.minus(Duration.ofDays(7)))
                || command.occurredAt().isAfter(now.plusSeconds(60))) {
            log.warn("Notification email event rejected outside acceptance contract: type={}", command.eventType());
            return;
        }
        var setting = policy.lockCurrent();
        if (!setting.enabled()) {
            return;
        }
        var recipient = recipients.lockCurrent(command.recipientId()).orElse(null);
        if (recipient == null) {
            return;
        }
        var mode = preferences.lockCurrent(command.recipientId()).get(command.category());
        if (mode == null || mode == NotificationEmailMode.OFF) {
            return;
        }
        Long skillId = safeSkillId(command);
        var candidate = new NotificationEmailItem(0, 0, command.recipientId(), command.eventKey(), command.category(),
                command.eventType(), skillId, command.entityId(), command.occurredAt(), now, "ACTIVE");
        if (!eligibility.eligible(candidate)) {
            return;
        }
        long seconds = setting.digestWindowMinutes() * 60L;
        long start = Math.floorDiv(now.getEpochSecond(), seconds) * seconds;
        for (int late = 0; late < 100; late++) {
            String key = mode == NotificationEmailMode.IMMEDIATE
                    ? "immediate:" + command.eventKey()
                    : "digest:" + seconds + ":" + (start + late * seconds) + ":r" + recipient.emailRevision();
            Instant due =
                    mode == NotificationEmailMode.IMMEDIATE ? now : Instant.ofEpochSecond(start + (late + 1) * seconds);
            var batch =
                    repository.openBatch(command.recipientId(), mode.name(), key, recipient.emailRevision(), due, now);
            if ("PENDING".equals(batch.status())) {
                repository.addItem(batch.id(), command, skillId, now);
                return;
            }
            if (mode == NotificationEmailMode.IMMEDIATE) {
                return; // duplicate already sealed/terminal instance
            }
        }
        throw new IllegalStateException("No open notification email digest window");
    }

    private Long safeSkillId(NotificationEmailCommand command) {
        if ("SKILL".equals(command.entityType())) {
            return command.entityId();
        }
        try {
            var node = mapper.readTree(command.bodyJson());
            return node.has("skillId") && node.get("skillId").isIntegralNumber() ? node.get("skillId").longValue()
                                                                                 : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
