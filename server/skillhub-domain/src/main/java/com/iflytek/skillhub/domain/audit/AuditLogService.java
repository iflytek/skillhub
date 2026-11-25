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
    public AuditLog recordOrganizationCreated(String actorUserId,
                                              String organizationId,
                                              String requestId) {
        return auditLogRepository.save(AuditLog.organizationSuccess(
                actorUserId,
                "ORGANIZATION_CREATED",
                organizationId,
                requestId,
                Instant.now(clock)));
    }

    @Transactional
    public AuditLog recordOrganizationAdministration(String actorUserId,
                                                     String action,
                                                     String organizationId,
                                                     String targetRef,
                                                     String requestId) {
        return auditLogRepository.save(AuditLog.organizationSuccess(
                actorUserId, action, organizationId, targetRef, requestId, Instant.now(clock)));
    }

    @Transactional
    public AuditLog recordOrganizationLoginConnection(
            String actorUserId, String action, String organizationId, String connectionId,
            String requestId, boolean success, String detailJson
    ) {
        return auditLogRepository.save(AuditLog.organizationLoginConnection(
                actorUserId, action, organizationId, connectionId,
                requestId, success, detailJson, Instant.now(clock)));
    }
}
