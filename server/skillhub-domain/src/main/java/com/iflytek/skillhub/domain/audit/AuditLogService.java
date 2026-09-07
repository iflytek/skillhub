package com.iflytek.skillhub.domain.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Records audit log entries for administrative and security-relevant actions.
 */
@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public AuditLogService(AuditLogRepository auditLogRepository, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    @Transactional
    public AuditLog record(String actorUserId,
                           String action,
                           String targetType,
                           Long targetId,
                           String requestId,
                           String clientIp,
                           String userAgent,
                           String detailJson) {
        Instant createdAt = Instant.now(clock);
        return auditLogRepository.save(new AuditLog(
            actorUserId,
            action,
            targetType,
            targetId,
            requestId,
            clientIp,
            userAgent,
            detailJson,
            createdAt
        ));
    }

    @Transactional
    public AuditLog record(OrganizationAuditEvent event) {
        return auditLogRepository.save(new AuditLog(
                event.actorUserId(),
                event.action().name(),
                event.targetType().storageValue(),
                null,
                event.organizationId(),
                event.targetReference(),
                event.result().name(),
                event.requestId(),
                null,
                null,
                event.detail().toJson(),
                Instant.now(clock)
        ));
    }
}
