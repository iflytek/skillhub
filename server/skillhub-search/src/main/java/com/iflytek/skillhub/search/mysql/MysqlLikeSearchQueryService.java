package com.iflytek.skillhub.search.mysql;

import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.search.SearchQuery;
import com.iflytek.skillhub.search.SearchQueryService;
import com.iflytek.skillhub.search.SearchResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
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
        Set<Long> memberNamespaceIds = query.visibilityScope().memberNamespaceIds().isEmpty()
                ? Set.of(-1L)
                : query.visibilityScope().memberNamespaceIds();

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT d.skillId ");
        sql.append("FROM SkillSearchDocumentEntity d, Skill s, Namespace n ");
        sql.append("WHERE d.skillId = s.id ");
        sql.append("AND d.namespaceId = n.id ");
        sql.append("AND (d.visibility = 'PUBLIC' ");
        if (query.visibilityScope().userId() != null) {
            sql.append("OR (d.visibility = 'NAMESPACE_ONLY' AND d.namespaceId IN :memberNamespaceIds) ");
        }
        sql.append(") ");
        sql.append("AND d.status = 'ACTIVE' ");
        sql.append("AND s.status = :skillStatusActive ");
        sql.append("AND s.hidden = FALSE ");
        sql.append("AND (n.status <> :namespaceStatusArchived ");
        if (query.visibilityScope().userId() != null) {
            sql.append("OR d.namespaceId IN :memberNamespaceIds ");
        }
        sql.append(") ");

        if (query.namespaceId() != null) {
            sql.append("AND d.namespaceId = :namespaceId ");
        }

        if (query.labelSlugs() != null && !query.labelSlugs().isEmpty()) {
            sql.append("AND d.skillId IN (");
            sql.append("SELECT sl.skillId FROM SkillLabel sl ");
            sql.append("JOIN LabelDefinition ld ON ld.id = sl.labelId ");
            sql.append("WHERE LOWER(ld.slug) IN :labelSlugs");
            sql.append(") ");
        }

        sql.append("ORDER BY s.updatedAt DESC, d.skillId DESC ");

        Query resultQuery = entityManager.createQuery(sql.toString(), Long.class);
        bindParameters(resultQuery, query, memberNamespaceIds);
        resultQuery.setFirstResult(query.page() * query.size());
        resultQuery.setMaxResults(query.size());

        @SuppressWarnings("unchecked")
        List<Long> skillIds = resultQuery.getResultList();

        String countSql = sql.toString().replaceFirst("SELECT d\\.skillId", "SELECT COUNT(d.skillId)");
        int orderByIndex = countSql.indexOf("ORDER BY");
        if (orderByIndex >= 0) {
            countSql = countSql.substring(0, orderByIndex);
        }

        Query countQuery = entityManager.createQuery(countSql, Long.class);
        bindParameters(countQuery, query, memberNamespaceIds);
        long total = (Long) countQuery.getSingleResult();

        return new SearchResult(skillIds, total, query.page(), query.size());
    }

    EntityManager entityManager() {
        return entityManager;
    }

    private void bindParameters(Query query, SearchQuery searchQuery, Set<Long> memberNamespaceIds) {
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
    }
}
