package com.iflytek.skillhub.search;

import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentJpaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Storage-engine-neutral search document writer backed by the shared JPA table.
 */
@Service
@ConditionalOnProperty(prefix = "skillhub.search", name = "engine", havingValue = "h2")
public class JpaSearchIndexService extends AbstractJpaSearchIndexService {

    public JpaSearchIndexService(SkillSearchDocumentJpaRepository repository,
                                 SearchEmbeddingService searchEmbeddingService) {
        super(repository, searchEmbeddingService);
    }
}
