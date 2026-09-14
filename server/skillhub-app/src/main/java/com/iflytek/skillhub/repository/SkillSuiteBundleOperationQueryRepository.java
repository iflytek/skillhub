package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMode;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleOperationStatus;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.SkillSuiteBundleOperationSummaryResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Dashboard read model for active Bundle operations owned by one actor.
 *
 * <p>The native query pages operations before joining member rows, then computes status counts in
 * PostgreSQL. This keeps each poll bounded and avoids loading member errors and warnings.</p>
 */
@Repository
public class SkillSuiteBundleOperationQueryRepository {

    private static final List<String> ACTIVE_STATUSES = List.of(
            SkillSuiteBundleOperationStatus.RUNNING.name(),
            SkillSuiteBundleOperationStatus.WAITING_FOR_MEMBERS.name(),
            SkillSuiteBundleOperationStatus.BLOCKED_RETRYABLE.name());

    private final EntityManager entityManager;

    public SkillSuiteBundleOperationQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public PageResponse<SkillSuiteBundleOperationSummaryResponse> findActive(
            String actorId,
            int page,
            int size
    ) {
        Query select = entityManager.createNativeQuery("""
                WITH active_operation AS (
                    SELECT operation.operation_id, operation.mode,
                           '@' || namespace.slug || '/' || operation.target_suite_slug AS target_coordinate,
                           operation.target_version, operation.status, operation.failure_code,
                           base_version.version AS base_version, operation.updated_at
                    FROM skill_suite_bundle_operation operation
                    JOIN namespace ON namespace.id = operation.namespace_id
                    LEFT JOIN skill_suite_version base_version
                           ON base_version.id = operation.base_suite_version_id
                    WHERE operation.actor_id = :actorId
                      AND operation.status IN (:statuses)
                    ORDER BY operation.updated_at DESC, operation.operation_id DESC
                    OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
                )
                SELECT operation.operation_id, operation.mode, operation.target_coordinate,
                       operation.target_version, operation.status, operation.failure_code,
                       operation.base_version, operation.updated_at,
                       COUNT(member.id) AS total_members,
                       COUNT(member.id) FILTER (WHERE member.status = 'COMPLETED') AS completed_members,
                       COUNT(member.id) FILTER (WHERE member.status = 'WAITING_FOR_MEMBER') AS waiting_members
                FROM active_operation operation
                LEFT JOIN skill_suite_bundle_member_result member
                       ON member.operation_id = operation.operation_id
                GROUP BY operation.operation_id, operation.mode, operation.target_coordinate,
                         operation.target_version, operation.status, operation.failure_code,
                         operation.base_version, operation.updated_at
                ORDER BY operation.updated_at DESC, operation.operation_id DESC
                """);
        bind(select, actorId)
                .setParameter("offset", (long) page * size)
                .setParameter("size", size);
        Query count = bind(entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM skill_suite_bundle_operation operation
                WHERE operation.actor_id = :actorId
                  AND operation.status IN (:statuses)
                """), actorId);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = select.getResultList();
        return new PageResponse<>(
                rows.stream().map(this::map).toList(),
                ((Number) count.getSingleResult()).longValue(), page, size);
    }

    private Query bind(Query query, String actorId) {
        return query.setParameter("actorId", actorId).setParameter("statuses", ACTIVE_STATUSES);
    }

    private SkillSuiteBundleOperationSummaryResponse map(Object[] row) {
        return new SkillSuiteBundleOperationSummaryResponse(
                (String) row[0], SkillSuiteBundleMode.valueOf(String.valueOf(row[1])),
                (String) row[2], (String) row[3],
                SkillSuiteBundleOperationStatus.valueOf(String.valueOf(row[4])),
                (String) row[5], (String) row[6],
                ((Number) row[8]).intValue(), ((Number) row[9]).intValue(),
                ((Number) row[10]).intValue(), instant(row[7]));
    }

    private Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        throw new IllegalStateException("Expected Bundle operation update timestamp, got " + value);
    }
}
