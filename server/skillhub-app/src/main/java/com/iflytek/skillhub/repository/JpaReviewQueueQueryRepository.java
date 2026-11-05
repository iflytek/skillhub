package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.review.ReviewTask;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL read model joining review subjects and immutable version metadata. Direct SQL keeps
 * authorization, keyword/kind filters, counts and pagination in one database predicate; a bounded
 * batch fetch then restores the domain tasks for the existing response projection.
 */
@Repository
@Transactional(readOnly = true)
public class JpaReviewQueueQueryRepository implements ReviewQueueQueryRepository {
    // Match SkillMetadata's String.strip, string-only package metadata and UTF-16 length limit.
    private static final String STRIP_CHARACTERS = "\t\n\u000b\f\r\u001c\u001d\u001e\u001f \u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2008\u2009\u200a\u2028\u2029\u205f\u3000";
    private static String strip(String expression) {
        return "btrim(" + expression + ", '" + STRIP_CHARACTERS + "')";
    }
    private static String packageText(String key) {
        String json = "version.parsed_metadata_json #> '{frontmatter,metadata," + key + "}'";
        return "CASE WHEN jsonb_typeof(" + json + ") = 'string' THEN NULLIF("
                + strip("version.parsed_metadata_json #>> '{frontmatter,metadata," + key + "}'") + ", '') END";
    }
    private static String displayName(String expression) {
        String value = strip(expression);
        // PostgreSQL length counts code points; Java's limit counts supplementary characters twice.
        String javaLength = "length(" + value + ") + length(regexp_replace(" + value + ", '[^𐀀-􏿿]', '', 'g'))";
        return "CASE WHEN " + javaLength + " <= 200 THEN NULLIF(" + value + ", '') END";
    }
    private static final String NAME = "CASE WHEN task.subject_type = 'SUITE_VERSION' "
            + "THEN COALESCE(suite_version.display_name, suite.display_name, suite.slug) ELSE COALESCE("
            + displayName("version.parsed_metadata_json ->> 'displayNameOverride'") + ", "
            + displayName(packageText("display-name")) + ", version.parsed_metadata_json ->> 'name', "
            + "skill.display_name, skill.slug) END ";
    private static final String SUMMARY = "CASE WHEN task.subject_type = 'SUITE_VERSION' "
            + "THEN CASE WHEN suite_version.id IS NOT NULL THEN suite_version.summary ELSE suite.summary END "
            + "ELSE COALESCE(version.parsed_metadata_json ->> 'shortDescriptionOverride', "
            + packageText("short-description") + ", version.parsed_metadata_json ->> 'description', skill.summary) END ";
    private static final String KIND = """
            CASE WHEN task.subject_type = 'SUITE_VERSION' THEN 'SUITE_PUBLISH'
              WHEN task.requested_visibility IS NOT NULL THEN 'VISIBILITY_CHANGE'
              WHEN task.subject_type = 'SKILL_METADATA' THEN 'AVATAR_CHANGE'
              WHEN EXISTS (SELECT 1 FROM skill_version previous
                WHERE previous.skill_id = task.subject_id
                  AND previous.id <> task.subject_version_id
                  AND previous.published_at <= task.submitted_at) THEN 'VERSION_UPDATE'
              ELSE 'FIRST_PUBLISH' END
            """;
    private static final String FROM = """
            FROM review_task task
            JOIN namespace ns ON ns.id = task.namespace_id
            LEFT JOIN skill ON task.subject_type IN ('SKILL_VERSION', 'SKILL_METADATA')
                              AND skill.id = task.subject_id
            LEFT JOIN skill_suite suite ON task.subject_type = 'SUITE_VERSION' AND suite.id = task.subject_id
            LEFT JOIN skill_suite_version suite_version ON task.subject_type = 'SUITE_VERSION'
                              AND suite_version.id = task.subject_version_id
            LEFT JOIN skill_version version ON task.subject_type = 'SKILL_VERSION'
                              AND version.id = task.subject_version_id
            LEFT JOIN user_account author ON author.id = task.submitted_by
            """;
    private final EntityManager entityManager;

    public JpaReviewQueueQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Page<Entry> list(Filter filter, boolean platformReviewer, Set<Long> managedNamespaceIds,
                            Pageable pageable) {
        Map<String, Object> parameters = new HashMap<>();
        StringBuilder where = new StringBuilder(" WHERE ");
        if (platformReviewer) {
            where.append("TRUE");
        } else if (managedNamespaceIds.isEmpty()) {
            return Page.empty(pageable);
        } else {
            where.append("ns.type = 'TEAM' AND ns.id IN (:managedNamespaceIds)");
            parameters.put("managedNamespaceIds", managedNamespaceIds);
        }
        if (filter.namespaceId() != null) {
            where.append(" AND task.namespace_id = :namespaceId");
            parameters.put("namespaceId", filter.namespaceId());
        }
        if (filter.status() != null) {
            where.append(" AND task.status = :status");
            parameters.put("status", filter.status());
        }
        if (filter.originType() != null) {
            where.append(" AND task.origin_type = :originType");
            parameters.put("originType", filter.originType());
        }
        if (filter.reviewKind() != null) {
            where.append(" AND (").append(KIND).append(") = :reviewKind");
            parameters.put("reviewKind", filter.reviewKind());
        }
        if (!filter.query().isBlank()) {
            where.append(" AND (LOWER(").append(NAME).append(") LIKE :query ESCAPE '!' ")
                    .append(" OR LOWER(CONCAT(ns.slug, '/', COALESCE(skill.slug, suite.slug))) LIKE :query ESCAPE '!' ")
                    .append(" OR LOWER(author.display_name) LIKE :query ESCAPE '!' ")
                    .append(" OR LOWER(task.submitted_by) LIKE :query ESCAPE '!')");
            String value = filter.query().strip().toLowerCase(java.util.Locale.ROOT);
            if (value.startsWith("@")) value = value.substring(1);
            parameters.put("query", "%" + value.replace("!", "!!").replace("%", "!%")
                    .replace("_", "!_") + "%");
        }
        long total = ((Number) bind(entityManager.createNativeQuery("SELECT COUNT(*) " + FROM + where),
                parameters).getSingleResult()).longValue();
        String direction = pageable.getSort().getOrderFor("submittedAt") != null
                && pageable.getSort().getOrderFor("submittedAt").isAscending() ? "ASC" : "DESC";
        @SuppressWarnings("unchecked")
        List<Object[]> rows = bind(entityManager.createNativeQuery("SELECT task.id, " + NAME + ", "
                        + SUMMARY + ", " + KIND + FROM + where
                        + " ORDER BY task.submitted_at " + direction + ", task.id " + direction), parameters)
                .setFirstResult(Math.toIntExact(pageable.getOffset())).setMaxResults(pageable.getPageSize())
                .getResultList();
        if (rows.isEmpty()) return new PageImpl<>(List.of(), pageable, total);
        List<Long> ids = rows.stream().map(row -> ((Number) row[0]).longValue()).toList();
        Map<Long, ReviewTask> tasks = new HashMap<>();
        entityManager.createQuery("SELECT task FROM ReviewTask task WHERE task.id IN :ids", ReviewTask.class)
                .setParameter("ids", ids).getResultList().forEach(task -> tasks.put(task.getId(), task));
        List<Entry> entries = rows.stream().map(row -> new Entry(tasks.get(((Number) row[0]).longValue()),
                (String) row[1], (String) row[2], (String) row[3])).toList();
        return new PageImpl<>(entries, pageable, total);
    }

    private Query bind(Query query, Map<String, Object> parameters) {
        parameters.forEach(query::setParameter);
        return query;
    }
}
