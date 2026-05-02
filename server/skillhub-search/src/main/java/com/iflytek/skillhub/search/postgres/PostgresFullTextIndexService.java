package com.iflytek.skillhub.search.postgres;

import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentJpaRepository;
import com.iflytek.skillhub.search.AbstractJpaSearchIndexService;
import com.iflytek.skillhub.search.SearchEmbeddingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * PostgreSQL-backed search index writer that stores searchable documents and semantic vectors.
 */
@Service
@ConditionalOnProperty(prefix = "skillhub.search", name = "engine", havingValue = "postgres", matchIfMissing = true)
public class PostgresFullTextIndexService extends AbstractJpaSearchIndexService {

    public PostgresFullTextIndexService(SkillSearchDocumentJpaRepository repository,
                                        SearchEmbeddingService searchEmbeddingService) {
        super(repository, searchEmbeddingService);
    }
}
