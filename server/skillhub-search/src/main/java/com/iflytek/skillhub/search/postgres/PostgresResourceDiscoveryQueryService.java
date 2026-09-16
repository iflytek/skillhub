package com.iflytek.skillhub.search.postgres;

import com.iflytek.skillhub.search.ResourceDiscoveryQueryService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL implementation of typed Skill and Suite discovery.
 *
 * <p>The native query is intentional: one stable page must union two independently versioned
 * resource tables, compute current Suite member availability, and sort/count the combined result
 * without loading member aggregates or introducing N+1 queries.</p>
 */
@Service
public class PostgresResourceDiscoveryQueryService implements ResourceDiscoveryQueryService {

    private static final String RESOURCE_CTE = """
            WITH resources AS (
                SELECT 'SKILL' AS resource_type,
                       skill.id AS resource_id,
                       namespace.id AS namespace_id,
                       namespace.slug AS namespace_slug,
                       skill.slug,
                       skill.display_name,
                       skill.summary,
                       version.version,
                       skill.visibility,
                       skill.download_count AS install_count,
                       TRUE AS available,
                       skill.updated_at
                FROM skill
                JOIN namespace ON namespace.id = skill.namespace_id
                JOIN skill_version version ON version.id = skill.latest_version_id
                WHERE skill.status = 'ACTIVE'
                  AND skill.hidden = FALSE
                  AND namespace.status <> 'ARCHIVED'
                  AND version.status = 'PUBLISHED'
                  AND version.download_ready = TRUE
                  AND version.yanked_at IS NULL
                  AND (skill.visibility = 'PUBLIC'
                       OR (skill.visibility = 'NAMESPACE_ONLY' AND skill.namespace_id IN (:memberNamespaceIds)))

                UNION ALL

                SELECT 'SUITE' AS resource_type,
                       suite.id AS resource_id,
                       namespace.id AS namespace_id,
                       namespace.slug AS namespace_slug,
                       suite.slug,
                       version.display_name,
                       version.summary,
                       version.version,
                       version.visibility,
                       suite.install_request_count AS install_count,
                       NOT EXISTS (
                           SELECT 1
                           FROM skill_suite_version_member member
                           LEFT JOIN skill_version member_version ON member_version.id = member.skill_version_id
                           LEFT JOIN skill member_skill ON member_skill.id = member.skill_id
                           LEFT JOIN namespace member_namespace ON member_namespace.id = member_skill.namespace_id
                           WHERE member.suite_version_id = version.id
                             AND (
                                 member.skill_id IS NULL
                                 OR member.skill_version_id IS NULL
                                 OR member_version.skill_id <> member.skill_id
                                 OR member_skill.status <> 'ACTIVE'
                                 OR member_namespace.status <> 'ACTIVE'
                                 OR member_skill.hidden = TRUE
                                 OR member_version.status <> 'PUBLISHED'
                                 OR member_version.download_ready = FALSE
                                 OR member_version.yanked_at IS NOT NULL
                                 OR (version.visibility = 'PUBLIC' AND member_skill.visibility <> 'PUBLIC')
                                 OR (version.visibility = 'NAMESPACE_ONLY' AND NOT (
                                     member_skill.visibility = 'PUBLIC'
                                     OR (member_skill.namespace_id = suite.namespace_id
                                         AND member_skill.visibility = 'NAMESPACE_ONLY')
                                 ))
                                 OR (version.visibility = 'PRIVATE' AND NOT (
                                     member_skill.visibility = 'PUBLIC'
                                     OR member_skill.namespace_id = suite.namespace_id
                                 ))
                             )
                       ) AS available,
                       suite.updated_at
                FROM skill_suite suite
                JOIN namespace ON namespace.id = suite.namespace_id
                JOIN skill_suite_version version ON version.id = suite.latest_version_id
                WHERE suite.status = 'ACTIVE'
                  AND suite.hidden = FALSE
                  AND namespace.status <> 'ARCHIVED'
                  AND version.status = 'PUBLISHED'
                  AND (version.visibility = 'PUBLIC'
                       OR (version.visibility = 'NAMESPACE_ONLY' AND suite.namespace_id IN (:memberNamespaceIds)))
            )
            """;

    private static final String FILTERS = """
            WHERE (:resourceType = '' OR resource_type = :resourceType)
              AND (:namespace = '' OR namespace_slug = :namespace)
              AND (:query = ''
                   OR LOWER(slug) LIKE :queryPattern
                   OR LOWER(display_name) LIKE :queryPattern
                   OR LOWER(COALESCE(summary, '')) LIKE :queryPattern)
            """;

    private final EntityManager entityManager;

    public PostgresResourceDiscoveryQueryService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public ResourcePage search(ResourceQuery input) {
        String queryText = normalize(input.keyword());
        String namespaceSlug = normalize(input.namespace());
        String type = normalize(input.resourceType()).toUpperCase(Locale.ROOT);
        if (!type.isEmpty() && !Set.of("SKILL", "SUITE").contains(type)) {
            type = "";
        }
        String order = switch (input.sort() == null ? "newest" : input.sort()) {
            case "downloads" -> "install_count DESC, updated_at DESC, resource_type, resource_id DESC";
            case "relevance" -> "CASE WHEN LOWER(slug) = :query THEN 3 "
                    + "WHEN LOWER(display_name) = :query THEN 2 "
                    + "WHEN LOWER(slug) LIKE :queryPrefix THEN 1 ELSE 0 END DESC, "
                    + "updated_at DESC, resource_type, resource_id DESC";
            default -> "updated_at DESC, resource_type, resource_id DESC";
        };
        String selectSql = RESOURCE_CTE + """
                SELECT resource_type, resource_id, namespace_slug, slug, display_name, summary,
                       version, visibility, install_count, available, updated_at
                FROM resources
                """ + FILTERS + " ORDER BY " + order + " OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY";
        String countSql = RESOURCE_CTE + "SELECT COUNT(*) FROM resources " + FILTERS;
        Query select = bind(entityManager.createNativeQuery(selectSql), input, queryText, namespaceSlug,
                type, "relevance".equals(input.sort()));
        select.setParameter("offset", (long) input.page() * input.size()).setParameter("size", input.size());
        Query count = bind(entityManager.createNativeQuery(countSql), input, queryText, namespaceSlug,
                type, false);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = select.getResultList();
        List<ResourceHit> items = rows.stream().map(this::map).toList();
        long total = ((Number) count.getSingleResult()).longValue();
        return new ResourcePage(items, total, input.page(), input.size());
    }

    private Query bind(
            Query query,
            ResourceQuery input,
            String keyword,
            String namespace,
            String resourceType,
            boolean relevance
    ) {
        Set<Long> memberNamespaceIds = input.memberNamespaceIds();
        query.setParameter("query", keyword)
                .setParameter("queryPattern", "%" + keyword + "%")
                .setParameter("namespace", namespace)
                .setParameter("resourceType", resourceType)
                .setParameter("memberNamespaceIds",
                        memberNamespaceIds == null || memberNamespaceIds.isEmpty()
                                ? Set.of(-1L)
                                : memberNamespaceIds);
        if (relevance) {
            query.setParameter("queryPrefix", keyword + "%");
        }
        return query;
    }

    private ResourceHit map(Object[] row) {
        return new ResourceHit(
                String.valueOf(row[0]),
                ((Number) row[1]).longValue(),
                (String) row[2],
                (String) row[3],
                (String) row[4],
                (String) row[5],
                (String) row[6],
                String.valueOf(row[7]),
                ((Number) row[8]).longValue(),
                (Boolean) row[9],
                instant(row[10]));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        throw new IllegalStateException("Expected resource discovery timestamp, got " + value);
    }
}
