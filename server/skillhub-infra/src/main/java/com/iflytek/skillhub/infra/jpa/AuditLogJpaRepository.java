package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.audit.AuditLog;
import com.iflytek.skillhub.domain.audit.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * JPA-backed audit-log repository that adds specification-based filtering for admin queries.
 */
@Repository
public interface AuditLogJpaRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog>, AuditLogRepository {

    @Override
    default Page<AuditLog> search(String actorUserId, String action, Pageable pageable) {
        Specification<AuditLog> specification = Specification.where(null);
        if (actorUserId != null && !actorUserId.isBlank()) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("actorUserId"), actorUserId));
        }
        if (action != null && !action.isBlank()) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("action"), action));
        }
        return findAll(specification, pageable);
    }
}
