package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.dto.MySkillSuiteSummaryResponse;
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
                       version.visibility, suite.hidden, suite.updated_at
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
