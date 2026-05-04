package com.iflytek.skillhub.search.mysql;

import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentJpaRepository;
import com.iflytek.skillhub.search.AbstractJpaSearchIndexService;
import com.iflytek.skillhub.search.SearchEmbeddingService;
import com.iflytek.skillhub.search.SearchIndexService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Transitional MySQL search document writer backed by the shared JPA search document table.
 */
@Service
@ConditionalOnProperty(prefix = "skillhub.search", name = "provider", havingValue = "mysql-like")
public class MysqlNoopSearchIndexService extends AbstractJpaSearchIndexService implements SearchIndexService {

    public MysqlNoopSearchIndexService(SkillSearchDocumentJpaRepository repository,
                                       SearchEmbeddingService searchEmbeddingService) {
        super(repository, searchEmbeddingService);
    }
}
