package com.iflytek.skillhub.domain.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Read-side service for paginating audit log entries with simple filters.
 */
@Service
public class AuditLogQueryService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogQueryService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public Page<AuditLog> list(int page, int size, String actorUserId, String action) {
        return auditLogRepository.search(actorUserId, action, PageRequest.of(page, size));
    }
}
