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
    public NotificationEmailDeliveryQueryService(NotificationEmailDeliveryRepository repository,
            NotificationEmailEligibility eligibility, AuditLogService audit, Clock clock) {
        this.repository = repository;
        this.eligibility = eligibility;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NotificationEmailDeliveryDetailResponse detail(long id, String user, int page, int size) {
        var batch =
                repository.find(id)
                        .filter(value -> value.recipientId().equals(user))
                        .orElseThrow(() -> new DomainNotFoundException("error.notification.email.delivery.notFound"));
        var all = repository.items(id);
        var visible =
                all.stream().filter(item -> "ACTIVE".equals(item.status())).filter(eligibility::eligible).toList();
        var items = visible.stream()
                            .skip((long) page * size)
                            .limit(size)
                            .map(item
                                    -> new NotificationEmailDeliveryItemResponse(item.id(), item.category().name(),
                                            item.eventType(), item.occurredAt(), eligibility.targetPath(item)))
                            .toList();
        return new NotificationEmailDeliveryDetailResponse(id, batch.status(), batch.kind(), batch.createdAt(),
                batch.acceptedAt(), items, visible.size(), all.size() - visible.size(), page, size,
                pages(visible.size(), size));
    }

    @Transactional(readOnly = true)
    public NotificationEmailDeliveryAdminPageResponse list(String status, int page, int size) {
        if (status != null
                && !Set.of("PENDING", "CLAIMED", "SENDING", "SENT", "RETRY", "FAILED", "UNKNOWN", "SUPPRESSED")
                        .contains(status))
            throw new DomainBadRequestException("error.notification.email.delivery.status.invalid");
        long count = repository.count(status);
        var items = repository.list(status, Math.multiplyExact(page, size), size)
                            .stream()
                            .map(row
                                    -> new NotificationEmailDeliveryAdminResponse(row.id(), row.recipientId(),
                                            row.kind(), row.status(), row.attempts(), row.resultCode(), row.createdAt(),
                                            row.acceptedAt()))
                            .toList();
        return new NotificationEmailDeliveryAdminPageResponse(items, count, page, size, pages(count, size));
    }

    @Transactional
    public void retry(long id, String actor, boolean confirmDuplicateRisk) {
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
                        + "}");
    }

    private static int pages(long count, int size) {
        return (int) ((count + size - 1) / size);
    }
}
