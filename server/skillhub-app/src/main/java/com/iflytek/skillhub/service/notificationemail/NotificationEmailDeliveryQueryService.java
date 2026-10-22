package com.iflytek.skillhub.service.notificationemail;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.*;
import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.notification.email.delivery.*;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationEmailDeliveryQueryService {
    private final NotificationEmailDeliveryRepository repository;
    private final NotificationEmailEligibility eligibility;
    private final AuditLogService audit;
    private final Clock clock;
    private final com.iflytek.skillhub.notification.email.NotificationEmailPolicyProvider policy;
    private final com.iflytek.skillhub.notification.email.NotificationEmailRecipientProvider recipients;
    public NotificationEmailDeliveryQueryService(NotificationEmailDeliveryRepository repository,
            NotificationEmailEligibility eligibility, AuditLogService audit, Clock clock,
            com.iflytek.skillhub.notification.email.NotificationEmailPolicyProvider policy,
            com.iflytek.skillhub.notification.email.NotificationEmailRecipientProvider recipients) {
        this.repository = repository;
        this.eligibility = eligibility;
        this.audit = audit;
        this.clock = clock;
        this.policy = policy;
        this.recipients = recipients;
    }

    @Transactional(readOnly = true)
    public NotificationEmailDeliveryDetailResponse detail(long id, String user, String cursor, int size) {
        var batch =
                repository.find(id)
                        .filter(value -> value.recipientId().equals(user))
                        .orElseThrow(() -> new DomainNotFoundException("error.notification.email.delivery.notFound"));
        long after;
        try {
            after = cursor == null ? 0 : Long.parseLong(cursor);
        } catch (NumberFormatException invalid) {
            throw new DomainBadRequestException("error.notification.email.delivery.status.invalid");
        }
        if (after < 0 || size < 1 || size > 100)
            throw new DomainBadRequestException("error.notification.email.delivery.status.invalid");
        var raw = repository.itemPage(id, after, size + 1);
        var scanned = raw.subList(0, Math.min(size, raw.size()));
        // A MERGED source has the same logical outcome as its parent, while retaining its original raw rows.
        var effective = batch.mergedIntoDeliveryId() == null
                ? batch
                : repository.find(batch.mergedIntoDeliveryId())
                          .filter(parent -> parent.recipientId().equals(user))
                          .orElse(batch);
        boolean suppressed = "SUPPRESSED".equals(effective.status());
        var cancelled = "SENT".equals(effective.status())
                ? Set.<Long>of()
                : repository.cancelledItemIds(scanned.stream().map(NotificationEmailItem::id).toList());
        var items = scanned.stream()
                            .filter(item -> !suppressed && !cancelled.contains(item.id()))
                            .filter(item -> "ACTIVE".equals(item.status()))
                            .filter(eligibility::eligible)
                            .map(item
                                    -> new NotificationEmailDeliveryItemResponse(item.id(), item.category().name(),
                                            item.eventType(), item.occurredAt(), eligibility.targetPath(item)))
                            .toList();
        String next = raw.size() > size ? Long.toString(scanned.getLast().id()) : null;
        return new NotificationEmailDeliveryDetailResponse(id, batch.status(), batch.kind(), batch.createdAt(),
                batch.acceptedAt(), items, batch.itemCount(), scanned.size() - items.size(), size, next,
                batch.mergedIntoDeliveryId());
    }

    @Transactional(readOnly = true)
    public NotificationEmailDeliveryAdminPageResponse list(String status, int page, int size) {
        if (status != null
                && !Set.of("PREPARING", "MERGED", "PENDING", "CLAIMED", "SENDING", "SENT", "RETRY", "FAILED", "UNKNOWN",
                               "SUPPRESSED")
                        .contains(status))
            throw new DomainBadRequestException("error.notification.email.delivery.status.invalid");
        long count = repository.count(status);
        var items = repository.list(status, Math.multiplyExact(page, size), size)
                            .stream()
                            .map(row
                                    -> new NotificationEmailDeliveryAdminResponse(row.id(), row.recipientId(),
                                            row.kind(), row.status(), row.attempts(), row.resultCode(), row.createdAt(),
                                            row.acceptedAt(), row.mergedIntoDeliveryId()))
                            .toList();
        return new NotificationEmailDeliveryAdminPageResponse(items, count, page, size, pages(count, size));
    }

    @Transactional
    public void retry(long id, String actor, boolean confirmDuplicateRisk) {
        var initial = repository.find(id).orElseThrow(
                () -> new DomainNotFoundException("error.notification.email.delivery.notFound"));
        policy.lockCurrent();
        recipients.lockCurrent(initial.recipientId());
        if (!repository.lockRecipient(initial.recipientId()))
            throw new DomainConflictException("error.notification.email.delivery.retry.conflict");
        var root = repository.activeRoot(initial.recipientId()).orElse(null);
        if (root != null && root.id() != id)
            throw new DomainConflictException("error.notification.email.delivery.retry.conflict");
        var batch = repository.lock(id);
        if (batch == null) {
            throw new DomainNotFoundException("error.notification.email.delivery.notFound");
        }
        if (!Set.of("FAILED", "UNKNOWN").contains(batch.status())) {
            throw new DomainBadRequestException("error.notification.email.delivery.retry.invalid");
        }
        if ("UNKNOWN".equals(batch.status()) && !confirmDuplicateRisk) {
            throw new DomainBadRequestException("error.notification.email.delivery.retry.duplicateRisk");
        }
        repository.retry(id, clock.instant());
        audit.record(actor, "NOTIFICATION_EMAIL_RETRY", "NOTIFICATION_EMAIL_DELIVERY", id, null, null, null,
                "{\"previousStatus\":\"" + batch.status() + "\",\"duplicateRiskAcknowledged\":" + confirmDuplicateRisk
                        + ",\"previousDeadline\":"
                        + (batch.retryDeadline() == null ? "null" : "\"" + batch.retryDeadline() + "\"")
                        + ",\"recoveryWindowReset\":true}");
    }

    private static int pages(long count, int size) {
        return (int) ((count + size - 1) / size);
    }
}
