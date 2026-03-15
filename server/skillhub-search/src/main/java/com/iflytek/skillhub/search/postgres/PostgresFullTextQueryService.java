package com.iflytek.skillhub.search.postgres;

import com.iflytek.skillhub.search.SearchQuery;
import com.iflytek.skillhub.search.SearchQueryService;
import com.iflytek.skillhub.search.SearchResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class PostgresFullTextQueryService implements SearchQueryService {
    private static final Pattern QUERY_TERM_SPLITTER = Pattern.compile("[^\\p{L}\\p{N}_]+");
    private static final int MAX_QUERY_TERMS = 8;
    private static final int SHORT_PREFIX_LENGTH = 2;
    private static final String TITLE_VECTOR_SQL = "to_tsvector('simple', coalesce(title, ''))";
    private static final String TITLE_SQL = "LOWER(title)";

    private final EntityManager entityManager;

    public PostgresFullTextQueryService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public SearchResult search(SearchQuery query) {
        String normalizedKeyword = normalizeKeyword(query.keyword());
        String tsQuery = buildPrefixTsQuery(normalizedKeyword);
        boolean hasKeyword = tsQuery != null;
        boolean useShortPrefixTitleSearch = hasKeyword && normalizedKeyword.length() <= SHORT_PREFIX_LENGTH;
        Set<Long> memberNamespaceIds = query.visibilityScope().memberNamespaceIds().isEmpty()
                ? Set.of(-1L)
                : query.visibilityScope().memberNamespaceIds();
        Set<Long> adminNamespaceIds = query.visibilityScope().adminNamespaceIds().isEmpty()
                ? Set.of(-1L)
                : query.visibilityScope().adminNamespaceIds();

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT skill_id FROM skill_search_document WHERE 1=1 ");

        // Visibility filtering
        sql.append("AND (visibility = 'PUBLIC' ");
        if (query.visibilityScope().userId() != null) {
            sql.append("OR (visibility = 'NAMESPACE_ONLY' AND namespace_id IN :memberNamespaceIds) ");
            sql.append("OR (visibility = 'PRIVATE' AND (namespace_id IN :adminNamespaceIds OR owner_id = :userId)) ");
        }
        sql.append(") ");

        // Status filtering
        sql.append("AND status = 'ACTIVE' ");

        // Namespace filtering
        if (query.namespaceId() != null) {
            sql.append("AND namespace_id = :namespaceId ");
        }

        // Full-text search
        if (hasKeyword) {
            sql.append("AND (");
            if (useShortPrefixTitleSearch) {
                sql.append(TITLE_VECTOR_SQL).append(" @@ to_tsquery('simple', :tsQuery) ");
            } else {
                sql.append("search_vector @@ to_tsquery('simple', :tsQuery) ");
            }
            sql.append(" OR ").append(TITLE_SQL).append(" LIKE :titleLike");
            sql.append(") ");
        }

        // Sorting
        if ("downloads".equals(query.sortBy())) {
            sql.append("ORDER BY (SELECT download_count FROM skill WHERE id = skill_id) DESC ");
        } else if ("rating".equals(query.sortBy())) {
            sql.append("ORDER BY (SELECT rating_avg FROM skill WHERE id = skill_id) DESC ");
        } else if ("newest".equals(query.sortBy())) {
            sql.append("ORDER BY (SELECT updated_at FROM skill WHERE id = skill_id) DESC ");
        } else if ("relevance".equals(query.sortBy()) && hasKeyword) {
            sql.append("ORDER BY CASE ");
            sql.append("WHEN ").append(TITLE_SQL).append(" = :titleExact THEN 4 ");
            sql.append("WHEN ").append(TITLE_SQL).append(" LIKE :titlePrefix THEN 3 ");
            sql.append("WHEN ").append(TITLE_SQL).append(" LIKE :titleLike THEN 2 ");
            sql.append("ELSE 1 END DESC, ");
            if (useShortPrefixTitleSearch) {
                sql.append("ts_rank_cd(").append(TITLE_VECTOR_SQL)
                        .append(", to_tsquery('simple', :tsQuery)) DESC, updated_at DESC ");
            } else {
                sql.append("ts_rank_cd(search_vector, to_tsquery('simple', :tsQuery)) DESC, updated_at DESC ");
            }
        } else {
            sql.append("ORDER BY updated_at DESC ");
        }

        // Pagination
        sql.append("LIMIT :limit OFFSET :offset");

        Query nativeQuery = entityManager.createNativeQuery(sql.toString());

        if (query.visibilityScope().userId() != null) {
            nativeQuery.setParameter("memberNamespaceIds", memberNamespaceIds);
            nativeQuery.setParameter("adminNamespaceIds", adminNamespaceIds);
            nativeQuery.setParameter("userId", query.visibilityScope().userId());
        }

        if (query.namespaceId() != null) {
            nativeQuery.setParameter("namespaceId", query.namespaceId());
        }

        if (hasKeyword) {
            nativeQuery.setParameter("tsQuery", tsQuery);
            nativeQuery.setParameter("titleExact", normalizedKeyword.toLowerCase());
            nativeQuery.setParameter("titlePrefix", normalizedKeyword.toLowerCase() + "%");
            nativeQuery.setParameter("titleLike", "%" + normalizedKeyword.toLowerCase() + "%");
        }

        nativeQuery.setParameter("limit", query.size());
        nativeQuery.setParameter("offset", query.page() * query.size());

        @SuppressWarnings("unchecked")
        List<Long> skillIds = (List<Long>) nativeQuery.getResultList().stream()
                .map(obj -> ((Number) obj).longValue())
                .toList();

        // Count total
        String countSql = sql.toString().replaceFirst("SELECT skill_id", "SELECT COUNT(*)");
        int orderByIndex = countSql.indexOf("ORDER BY");
        if (orderByIndex >= 0) {
            countSql = countSql.substring(0, orderByIndex);
        }
        int limitIndex = countSql.indexOf("LIMIT");
        if (limitIndex >= 0) {
            countSql = countSql.substring(0, limitIndex);
        }

        Query countQuery = entityManager.createNativeQuery(countSql);

        if (query.visibilityScope().userId() != null) {
            countQuery.setParameter("memberNamespaceIds", memberNamespaceIds);
            countQuery.setParameter("adminNamespaceIds", adminNamespaceIds);
            countQuery.setParameter("userId", query.visibilityScope().userId());
        }

        if (query.namespaceId() != null) {
            countQuery.setParameter("namespaceId", query.namespaceId());
        }

        if (hasKeyword) {
            countQuery.setParameter("tsQuery", tsQuery);
            countQuery.setParameter("titleLike", "%" + normalizedKeyword.toLowerCase() + "%");
        }

        long total = ((Number) countQuery.getSingleResult()).longValue();

        return new SearchResult(skillIds, total, query.page(), query.size());
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim().toLowerCase();
    }

    private String buildPrefixTsQuery(String keyword) {
        if (keyword == null) {
            return null;
        }

        List<String> terms = QUERY_TERM_SPLITTER.splitAsStream(keyword.toLowerCase())
                .map(String::trim)
                .filter(term -> !term.isBlank())
                .distinct()
                .limit(MAX_QUERY_TERMS)
                .toList();

        if (terms.isEmpty()) {
            return null;
        }

        return terms.stream()
                .map(term -> term + ":*")
                .reduce((left, right) -> left + " & " + right)
                .orElse(null);
    }
}
