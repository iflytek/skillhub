package com.iflytek.skillhub.search.mysql;

import com.iflytek.skillhub.search.SearchRebuildService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Transitional no-op rebuild service for the mysql-like search provider.
 */
@Service
@ConditionalOnProperty(prefix = "skillhub.search", name = "provider", havingValue = "mysql-like")
public class MysqlNoopSearchRebuildService implements SearchRebuildService {

    @Override
    public void rebuildAll() {
        // No-op until the MySQL runtime gets a dedicated rebuild implementation.
    }

    @Override
    public void rebuildByNamespace(Long namespaceId) {
        // No-op until the MySQL runtime gets a dedicated rebuild implementation.
    }

    @Override
    public void rebuildBySkill(Long skillId) {
        // No-op until the MySQL runtime gets a dedicated rebuild implementation.
    }
}
