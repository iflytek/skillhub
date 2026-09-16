package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.dto.ReviewProgressPageResponse;
import com.iflytek.skillhub.dto.ReviewProgressResponse;
import com.iflytek.skillhub.dto.ReviewProgressStatusCounts;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL read-model query for review progress.
 *
 * <p>Direct SQL is intentional here: the page boundary applies to grouped resource-version attempts,
 * not individual review tasks. Window functions keep grouping, latest-attempt selection, counts,
 * filtering, and pagination in the database instead of loading an author's full history.</p>
 */
@Repository
public class JpaReviewProgressQueryRepository implements ReviewProgressQueryRepository {

    private static final String RANKED_CTE = """
            WITH ranked AS (
                SELECT task.id,
                       task.skill_id,
                       task.subject_type,
                       task.subject_id,
                       task.subject_version_id,
                       task.subject_version,
                       task.namespace_id,
                       task.status,
                       task.review_comment,
                       task.submitted_at,
                       task.reviewed_at,
                       ROW_NUMBER() OVER (
                           PARTITION BY task.subject_type, task.subject_id, task.subject_version
                           ORDER BY task.submitted_at DESC, task.id DESC
                       ) AS attempt_rank,
                       COUNT(*) OVER (
                           PARTITION BY task.subject_type, task.subject_id, task.subject_version
                       ) AS attempt_count
                FROM review_task task
                WHERE task.submitted_by = :userId
            ), latest AS (
                SELECT *
                FROM ranked
                WHERE attempt_rank = 1
            )
            """;

    private static final String MY_PROGRESS_SQL = RANKED_CTE + """
            SELECT latest.id,
                   latest.skill_id,
                   namespace.slug,
                   skill.slug,
                   latest.subject_version,
                   latest.status,
                   latest.review_comment,
                   latest.submitted_at,
                   latest.reviewed_at,
                   latest.attempt_count,
                   latest.subject_type,
                   latest.subject_id,
                   latest.subject_version_id,
                   COALESCE(skill.slug, suite.slug) AS subject_slug
            FROM latest
            LEFT JOIN skill
              ON latest.subject_type = 'SKILL_VERSION' AND skill.id = latest.subject_id
            LEFT JOIN skill_suite suite
              ON latest.subject_type = 'SUITE_VERSION' AND suite.id = latest.subject_id
            JOIN namespace ON namespace.id = latest.namespace_id
            WHERE (
                    :query = ''
                    OR LOWER(COALESCE(skill.slug, suite.slug)) LIKE :queryPattern
                    OR LOWER(namespace.slug) LIKE :queryPattern
                  )
              AND (:status = '' OR latest.status = :status)
            ORDER BY latest.submitted_at DESC, latest.id DESC
            OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
            """;

    private static final String MY_PROGRESS_SUMMARY_SQL = RANKED_CTE + """
            SELECT COUNT(*) FILTER (WHERE :status = '' OR latest.status = :status) AS filtered_total,
                   COUNT(*) FILTER (WHERE latest.status = 'PENDING') AS pending_count,
                   COUNT(*) FILTER (WHERE latest.status = 'APPROVED') AS approved_count,
                   COUNT(*) FILTER (WHERE latest.status = 'REJECTED') AS rejected_count
            FROM latest
            LEFT JOIN skill
              ON latest.subject_type = 'SKILL_VERSION' AND skill.id = latest.subject_id
            LEFT JOIN skill_suite suite
              ON latest.subject_type = 'SUITE_VERSION' AND suite.id = latest.subject_id
            JOIN namespace ON namespace.id = latest.namespace_id
            WHERE :query = ''
               OR LOWER(COALESCE(skill.slug, suite.slug)) LIKE :queryPattern
               OR LOWER(namespace.slug) LIKE :queryPattern
            """;

    private final EntityManager entityManager;

    public JpaReviewProgressQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewProgressPageResponse findMyProgress(
            String userId,
            ReviewTaskStatus status,
            String query,
            int page,
            int size) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        String statusName = status != null ? status.name() : "";
        String queryPattern = "%" + normalizedQuery + "%";
        Query nativeQuery = bindFilters(
                entityManager.createNativeQuery(MY_PROGRESS_SQL),
                userId,
                statusName,
                normalizedQuery,
                queryPattern)
                .setParameter("offset", (long) page * size)
                .setParameter("size", size);
        Query summaryQuery = bindFilters(
                entityManager.createNativeQuery(MY_PROGRESS_SUMMARY_SQL),
                userId,
                statusName,
                normalizedQuery,
                queryPattern);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = nativeQuery.getResultList();
        List<ReviewProgressResponse> items = rows.stream().map(this::mapRow).toList();
        Object[] summary = (Object[]) summaryQuery.getSingleResult();
        long total = number(summary[0]).longValue();
        ReviewProgressStatusCounts statusCounts = new ReviewProgressStatusCounts(
                number(summary[1]).longValue(),
                number(summary[2]).longValue(),
                number(summary[3]).longValue()
        );
        return new ReviewProgressPageResponse(items, total, page, size, statusCounts);
    }

    private Query bindFilters(
            Query query,
            String userId,
            String status,
            String normalizedQuery,
            String queryPattern) {
        return query
                .setParameter("userId", userId)
                .setParameter("status", status)
                .setParameter("query", normalizedQuery)
                .setParameter("queryPattern", queryPattern);
    }

    private ReviewProgressResponse mapRow(Object[] row) {
        return new ReviewProgressResponse(
                number(row[0]).longValue(),
                row[1] == null ? null : number(row[1]).longValue(),
                (String) row[2],
                (String) row[3],
                (String) row[4],
                String.valueOf(row[5]),
                (String) row[6],
                instant(row[7]),
                instant(row[8]),
                number(row[9]).longValue(),
                String.valueOf(row[10]),
                number(row[11]).longValue(),
                row[12] == null ? null : number(row[12]).longValue(),
                (String) row[13]
        );
    }

    private Number number(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalStateException("Expected numeric review progress value, got " + value);
    }

    private Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        throw new IllegalStateException("Expected review progress timestamp, got " + value.getClass().getName());
    }
}
