package com.iflytek.skillhub.domain.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuditLogRepository {
    AuditLog save(AuditLog auditLog);
    Page<AuditLog> search(String actorUserId, String action, Pageable pageable);
}
