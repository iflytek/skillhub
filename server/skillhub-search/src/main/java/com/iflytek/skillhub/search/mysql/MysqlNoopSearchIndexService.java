package com.iflytek.skillhub.search.mysql;

import com.iflytek.skillhub.search.SearchIndexService;
import com.iflytek.skillhub.search.SkillSearchDocument;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Transitional no-op index writer for the mysql-like search provider.
 */
@Service
@ConditionalOnProperty(prefix = "skillhub.search", name = "provider", havingValue = "mysql-like")
public class MysqlNoopSearchIndexService implements SearchIndexService {

    @Override
    public void index(SkillSearchDocument document) {
        // No-op until the MySQL runtime gets a dedicated index implementation.
    }

    @Override
    public void batchIndex(List<SkillSearchDocument> documents) {
        // No-op until the MySQL runtime gets a dedicated index implementation.
    }

    @Override
    public void remove(Long skillId) {
        // No-op until the MySQL runtime gets a dedicated index implementation.
    }
}
