package com.iflytek.skillhub.search.mysql;

import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.search.SearchQuery;
import com.iflytek.skillhub.search.SearchQueryService;
import com.iflytek.skillhub.search.SearchResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Transitional MySQL-oriented search provider boundary.
 *
 * <p>This skeleton intentionally avoids PostgreSQL FTS-specific SQL and
 * terminology. Follow-up stories extend it with MySQL-safe filtering,
 * matching, ordering, and paging behavior.
 */
@Service
@ConditionalOnProperty(prefix = "skillhub.search", name = "engine", havingValue = "mysql")
public class MysqlLikeSearchQueryService implements SearchQueryService {

    private final EntityManager entityManager;

    public MysqlLikeSearchQueryService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public SearchResult search(SearchQuery query) {
        String normalizedKeyword = normalizeKeyword(query.keyword());
        boolean hasKeyword = normalizedKeyword != null;
        boolean useRelevanceOrdering = "relevance".equals(query.sortBy()) && hasKeyword;
        Set<Long> memberNamespaceIds = query.visibilityScope().memberNamespaceIds().isEmpty()
                ? Set.of(-1L)
                : query.visibilityScope().memberNamespaceIds();

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT d.skill_id ");
        sql.append("FROM skill_search_document d ");
        sql.append("JOIN skill s ON d.skill_id = s.id ");
        sql.append("JOIN namespace n ON d.namespace_id = n.id ");
        sql.append("AND (d.visibility = 'PUBLIC' ");
        if (query.visibilityScope().userId() != null) {
            sql.append("OR (d.visibility = 'NAMESPACE_ONLY' AND d.namespace_id IN (:memberNamespaceIds)) ");
        }
        sql.append(") ");
        sql.append("AND d.status = 'ACTIVE' ");
        sql.append("AND s.status = :skillStatusActive ");
        sql.append("AND s.hidden = false ");
        sql.append("AND (n.status <> :namespaceStatusArchived ");
        if (query.visibilityScope().userId() != null) {
            sql.append("OR d.namespace_id IN (:memberNamespaceIds) ");
        }
        sql.append(") ");

        if (query.namespaceId() != null) {
            sql.append("AND d.namespace_id = :namespaceId ");
        }

        if (query.labelSlugs() != null && !query.labelSlugs().isEmpty()) {
            sql.append("AND d.skill_id IN (");
            sql.append("SELECT sl.skill_id FROM skill_label sl ");
            sql.append("JOIN label_definition ld ON ld.id = sl.label_id ");
            sql.append("WHERE LOWER(ld.slug) IN (:labelSlugs)");
            sql.append(") ");
        }

        if (hasKeyword) {
            sql.append("AND (");
            sql.append("LOWER(COALESCE(d.title, '')) LIKE :titleLike ");
            sql.append("OR LOWER(COALESCE(d.summary, '')) LIKE :titleLike ");
            sql.append("OR LOWER(COALESCE(d.keywords, '')) LIKE :titleLike ");
            sql.append("OR LOWER(COALESCE(d.search_text, '')) LIKE :titleLike");
            sql.append(") ");
        }

        if ("downloads".equals(query.sortBy())) {
            sql.append("ORDER BY s.download_count DESC, s.updated_at DESC, d.skill_id DESC ");
        } else if ("rating".equals(query.sortBy())) {
            sql.append("ORDER BY s.rating_avg DESC, s.updated_at DESC, d.skill_id DESC ");
        } else if ("newest".equals(query.sortBy())) {
            sql.append("ORDER BY s.updated_at DESC, d.skill_id DESC ");
        } else if (useRelevanceOrdering) {
            sql.append("ORDER BY CASE ");
            sql.append("WHEN LOWER(COALESCE(d.title, '')) = :titleExact THEN 4 ");
            sql.append("WHEN LOWER(COALESCE(d.title, '')) LIKE :titlePrefix THEN 3 ");
            sql.append("WHEN LOWER(COALESCE(d.title, '')) LIKE :titleLike THEN 2 ");
            sql.append("ELSE 1 END DESC, s.updated_at DESC, d.skill_id DESC ");
        } else {
            sql.append("ORDER BY s.updated_at DESC, d.skill_id DESC ");
        }
        sql.append("LIMIT :limit OFFSET :offset");

        Query resultQuery = entityManager.createNativeQuery(sql.toString());
        bindParameters(resultQuery, query, memberNamespaceIds, normalizedKeyword, useRelevanceOrdering);
        resultQuery.setParameter("limit", query.size());
        resultQuery.setParameter("offset", query.page() * query.size());

        @SuppressWarnings("unchecked")
        List<Number> rawSkillIds = resultQuery.getResultList();
        List<Long> skillIds = rawSkillIds.stream().map(Number::longValue).toList();

        String countSql = sql.toString().replaceFirst("SELECT d\\.skill_id", "SELECT COUNT(d.skill_id)");
        int orderByIndex = countSql.indexOf("ORDER BY");
        if (orderByIndex >= 0) {
            countSql = countSql.substring(0, orderByIndex);
        }

        Query countQuery = entityManager.createNativeQuery(countSql);
        bindParameters(countQuery, query, memberNamespaceIds, normalizedKeyword, false);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        return new SearchResult(skillIds, total, query.page(), query.size());
    }

    private void bindParameters(Query query,
                                SearchQuery searchQuery,
                                Set<Long> memberNamespaceIds,
                                String normalizedKeyword,
                                boolean useRelevanceOrdering) {
        if (searchQuery.visibilityScope().userId() != null) {
            query.setParameter("memberNamespaceIds", memberNamespaceIds);
        }
        if (searchQuery.namespaceId() != null) {
            query.setParameter("namespaceId", searchQuery.namespaceId());
        }
        query.setParameter("skillStatusActive", SkillStatus.ACTIVE);
        query.setParameter("namespaceStatusArchived", NamespaceStatus.ARCHIVED);
        if (searchQuery.labelSlugs() != null && !searchQuery.labelSlugs().isEmpty()) {
            query.setParameter("labelSlugs", searchQuery.labelSlugs());
        }
        if (normalizedKeyword != null) {
            query.setParameter("titleLike", "%" + normalizedKeyword + "%");
            if (useRelevanceOrdering) {
                query.setParameter("titleExact", normalizedKeyword);
                query.setParameter("titlePrefix", normalizedKeyword + "%");
            }
        }
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim().toLowerCase(Locale.ROOT);
    }
}
