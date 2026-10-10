package com.iflytek.skillhub.domain.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Domain repository contract for audit-log persistence and filtered pagination.
 */
public interface AuditLogRepository {
    AuditLog save(AuditLog auditLog);
    Page<AuditLog> search(String actorUserId, String action, Pageable pageable);
}
