package com.iflytek.skillhub.search.postgres;

import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentJpaRepository;
import com.iflytek.skillhub.search.SearchEmbeddingService;
import com.iflytek.skillhub.search.SearchIndexService;
import com.iflytek.skillhub.search.SkillSearchDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL-backed search index writer that stores searchable documents and semantic vectors.
 */
@Service
public class PostgresFullTextIndexService implements SearchIndexService {

    private final SkillSearchDocumentJpaRepository repository;
    private final SearchEmbeddingService searchEmbeddingService;

    public PostgresFullTextIndexService(SkillSearchDocumentJpaRepository repository,
                                        SearchEmbeddingService searchEmbeddingService) {
        this.repository = repository;
        this.searchEmbeddingService = searchEmbeddingService;
    }

    @Override
    @Transactional
    public void index(SkillSearchDocument document) {
        Optional<SkillSearchDocumentEntity> existing = repository.findBySkillId(document.skillId());

        if (existing.isPresent()) {
            SkillSearchDocumentEntity entity = existing.get();
            entity.setNamespaceId(document.namespaceId());
            entity.setNamespaceSlug(document.namespaceSlug());
            entity.setOwnerId(document.ownerId());
            entity.setTitle(document.title());
            entity.setSummary(document.summary());
            entity.setKeywords(document.keywords());
            entity.setSearchText(document.searchText());
            entity.setSemanticVector(buildSemanticVector(document));
            entity.setVisibility(document.visibility());
            entity.setStatus(document.status());
            repository.save(entity);
        } else {
            SkillSearchDocumentEntity entity = new SkillSearchDocumentEntity(
                    document.skillId(),
                    document.namespaceId(),
                    document.namespaceSlug(),
                    document.ownerId(),
                    document.title(),
                    document.summary(),
                    document.keywords(),
                    document.searchText(),
                    buildSemanticVector(document),
                    document.visibility(),
                    document.status()
            );
            repository.save(entity);
        }
    }

    @Override
    @Transactional
    public void batchIndex(List<SkillSearchDocument> documents) {
        for (SkillSearchDocument document : documents) {
            index(document);
        }
    }

    @Override
    @Transactional
    public void remove(Long skillId) {
        repository.deleteBySkillId(skillId);
    }

    private String buildSemanticVector(SkillSearchDocument document) {
        return searchEmbeddingService.embed(String.join("\n",
                safe(document.title()),
                safe(document.title()),
                safe(document.summary()),
                safe(document.keywords()),
                safe(document.searchText())));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
