package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.dto.MySkillSuiteSummaryResponse;
import com.iflytek.skillhub.dto.MySkillSuiteWorkspaceResponse;
import com.iflytek.skillhub.dto.PageResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard read model for Suite versions manageable by the current Namespace role.
 *
 * <p>The native query selects the newest version each caller may manage in one round trip. This
 * avoids loading every Suite and then resolving version ownership and Namespace roles with N+1
 * repository calls.</p>
 */
@Repository
public class MySkillSuiteQueryRepository {

    private static final String CTE = """
            WITH manageable AS (
                SELECT DISTINCT ON (suite.id)
                       suite.id, version.id AS version_id, namespace.slug AS namespace_slug,
                       suite.slug, version.display_name, version.summary, version.version,
                       version.status AS version_status, suite.status AS suite_status,
                       version.visibility, suite.hidden, suite.updated_at,
                       version.created_at AS version_created_at
                FROM skill_suite suite
                JOIN namespace ON namespace.id = suite.namespace_id
                JOIN skill_suite_version version ON version.suite_id = suite.id
                WHERE suite.namespace_id IN (:memberNamespaceIds)
                  AND (suite.created_by = :userId OR suite.namespace_id IN (:adminNamespaceIds))
                ORDER BY suite.id, version.created_at DESC, version.id DESC
            )
            """;

    private static final String FILTER = """
            WHERE (:query = '' OR LOWER(slug) LIKE :pattern
                   OR LOWER(display_name) LIKE :pattern
                   OR LOWER(COALESCE(summary, '')) LIKE :pattern)
            """;

    private final EntityManager entityManager;

    // The actor's newest operation is merged by coordinate, not fetched once per Suite. Operations
    // without a Suite remain temporary rows. Page selection and metrics each require one SQL query;
    // neither query loads plans, member results, or package contents.
    private static final String WORKSPACE_CTE = CTE.stripTrailing() + """
            , latest_operation AS (
                SELECT DISTINCT ON (operation.namespace_id, operation.target_suite_slug)
                       operation.operation_id, operation.namespace_id, operation.target_suite_slug,
                       operation.target_version, operation.status, operation.failure_code,
                       operation.created_at, operation.updated_at, namespace.slug AS namespace_slug
                FROM skill_suite_bundle_operation operation
                JOIN namespace ON namespace.id = operation.namespace_id
                WHERE operation.actor_id = :userId
                  AND operation.namespace_id IN (:memberNamespaceIds)
                ORDER BY operation.namespace_id, operation.target_suite_slug,
                         operation.created_at DESC, operation.operation_id DESC
            ), workspace AS (
                SELECT suite.id AS suite_id, suite.namespace_slug, suite.slug,
                       suite.display_name, suite.summary,
                       COALESCE(operation.target_version, suite.version) AS version,
                       suite.version AS suite_version,
                       CASE WHEN operation.status IN ('BLOCKED_RETRYABLE', 'REPREVIEW_REQUIRED') THEN 'ATTENTION'
                            WHEN operation.status IS NOT NULL THEN 'PREPARING'
                            WHEN suite.suite_status = 'ARCHIVED' THEN 'ARCHIVED'
                            ELSE suite.version_status END AS state,
                       GREATEST(suite.updated_at, operation.updated_at) AS updated_at,
                       operation.operation_id, operation.status AS operation_status, operation.failure_code
                FROM manageable suite
                LEFT JOIN latest_operation operation
                  ON operation.namespace_slug = suite.namespace_slug AND operation.target_suite_slug = suite.slug
                 AND operation.status IN ('RUNNING', 'WAITING_FOR_MEMBERS', 'BLOCKED_RETRYABLE', 'REPREVIEW_REQUIRED')
                 AND operation.created_at >= suite.version_created_at
                UNION ALL
                SELECT CAST(NULL AS bigint), operation.namespace_slug, operation.target_suite_slug,
                       operation.target_suite_slug, CAST(NULL AS text), operation.target_version, CAST(NULL AS text),
                       CASE WHEN operation.status IN ('BLOCKED_RETRYABLE', 'REPREVIEW_REQUIRED') THEN 'ATTENTION'
                            WHEN operation.status = 'CANCELLED' THEN 'CANCELLED'
                            ELSE 'PREPARING' END,
                       operation.updated_at, operation.operation_id, operation.status, operation.failure_code
                FROM latest_operation operation
                WHERE NOT EXISTS (
                    SELECT 1 FROM skill_suite suite
                    WHERE suite.namespace_id = operation.namespace_id AND suite.slug = operation.target_suite_slug
                )
            ), searched AS (
                SELECT * FROM workspace
                WHERE (:query = '' OR LOWER(namespace_slug || '/' || slug) LIKE :pattern ESCAPE '!'
                       OR LOWER(display_name) LIKE :pattern ESCAPE '!'
                       OR LOWER(COALESCE(summary, '')) LIKE :pattern ESCAPE '!')
            )
            """;

    @Transactional(readOnly = true)
    public MySkillSuiteWorkspaceResponse findWorkspace(
            String userId, Set<Long> memberNamespaceIds, Set<Long> adminNamespaceIds,
            String keyword, String state, int page, int size
    ) {
        String queryText = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String pattern = "%" + queryText.replace("!", "!!").replace("%", "!%")
                .replace("_", "!_") + "%";
        Query select = bind(entityManager.createNativeQuery(WORKSPACE_CTE + """
                SELECT suite_id, namespace_slug, slug, display_name, summary, version,
                       suite_version, state, updated_at, operation_id, operation_status, failure_code
                FROM searched
                WHERE (:state = '' OR state = :state
                       OR (:state = 'OTHER' AND state NOT IN ('ATTENTION', 'DRAFT', 'PENDING_REVIEW', 'PUBLISHED')))
                ORDER BY CASE WHEN state = 'ATTENTION' THEN 0 WHEN state = 'PREPARING' THEN 1 ELSE 2 END,
                         updated_at DESC, namespace_slug, slug
                OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
                """), userId, memberNamespaceIds, adminNamespaceIds, queryText);
        select.setParameter("pattern", pattern).setParameter("state", state)
                .setParameter("offset", (long) page * size).setParameter("size", size);
        Query metrics = bind(entityManager.createNativeQuery(WORKSPACE_CTE + """
                SELECT COUNT(*) FILTER (WHERE :state = '' OR state = :state
                           OR (:state = 'OTHER' AND state NOT IN ('ATTENTION', 'DRAFT', 'PENDING_REVIEW', 'PUBLISHED'))),
                       COUNT(*) FILTER (WHERE state = 'ATTENTION'),
                       COALESCE(BOOL_OR(operation_status IN ('RUNNING', 'WAITING_FOR_MEMBERS')), FALSE)
                FROM searched
                """), userId, memberNamespaceIds, adminNamespaceIds, queryText);
        metrics.setParameter("pattern", pattern).setParameter("state", state);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = select.getResultList();
        Object[] counts = (Object[]) metrics.getSingleResult();
        return new MySkillSuiteWorkspaceResponse(rows.stream().map(row ->
                new MySkillSuiteWorkspaceResponse.Item(
                        row[0] == null ? null : ((Number) row[0]).longValue(),
                        (String) row[1], (String) row[2], (String) row[3], (String) row[4],
                        (String) row[5], (String) row[6], (String) row[7], instant(row[8]),
                        (String) row[9], (String) row[10], (String) row[11])).toList(),
                ((Number) counts[0]).longValue(), page, size,
                ((Number) counts[1]).longValue(), (Boolean) counts[2]);
    }

    public MySkillSuiteQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public PageResponse<MySkillSuiteSummaryResponse> findMine(
            String userId,
            Set<Long> memberNamespaceIds,
            Set<Long> adminNamespaceIds,
            String keyword,
            int page,
            int size
    ) {
        String queryText = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        Query select = bind(entityManager.createNativeQuery(CTE + """
                SELECT id, version_id, namespace_slug, slug, display_name, summary, version,
                       version_status, suite_status, visibility, hidden, updated_at
                FROM manageable
                """ + FILTER + " ORDER BY updated_at DESC, id DESC OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY"),
                userId, memberNamespaceIds, adminNamespaceIds, queryText);
        select.setParameter("offset", (long) page * size).setParameter("size", size);
        Query count = bind(entityManager.createNativeQuery(
                CTE + "SELECT COUNT(*) FROM manageable " + FILTER),
                userId, memberNamespaceIds, adminNamespaceIds, queryText);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = select.getResultList();
        List<MySkillSuiteSummaryResponse> items = rows.stream().map(this::map).toList();
        return new PageResponse<>(items, ((Number) count.getSingleResult()).longValue(), page, size);
    }

    private Query bind(
            Query query,
            String userId,
            Set<Long> memberNamespaceIds,
            Set<Long> adminNamespaceIds,
            String keyword
    ) {
        return query.setParameter("userId", userId)
                .setParameter("memberNamespaceIds", idsOrSentinel(memberNamespaceIds))
                .setParameter("adminNamespaceIds", idsOrSentinel(adminNamespaceIds))
                .setParameter("query", keyword)
                .setParameter("pattern", "%" + keyword + "%");
    }

    private Set<Long> idsOrSentinel(Set<Long> ids) {
        return ids.isEmpty() ? Set.of(-1L) : ids;
    }

    private MySkillSuiteSummaryResponse map(Object[] row) {
        return new MySkillSuiteSummaryResponse(
                ((Number) row[0]).longValue(), ((Number) row[1]).longValue(),
                (String) row[2], (String) row[3], (String) row[4], (String) row[5],
                (String) row[6], String.valueOf(row[7]), String.valueOf(row[8]),
                String.valueOf(row[9]), (Boolean) row[10], instant(row[11]));
    }

    private Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        throw new IllegalStateException("Expected Suite update timestamp, got " + value);
    }
}
