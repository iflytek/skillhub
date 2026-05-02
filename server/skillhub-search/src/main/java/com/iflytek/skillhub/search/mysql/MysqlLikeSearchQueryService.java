package com.iflytek.skillhub.search.mysql;

import com.iflytek.skillhub.search.SearchQuery;
import com.iflytek.skillhub.search.SearchQueryService;
import com.iflytek.skillhub.search.SearchResult;
import jakarta.persistence.EntityManager;
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
        throw new UnsupportedOperationException(
                "mysql-like search behavior is not implemented yet; " +
                        "follow-up stories add filtering, matching, and sorting.");
    }

    EntityManager entityManager() {
        return entityManager;
    }
}
