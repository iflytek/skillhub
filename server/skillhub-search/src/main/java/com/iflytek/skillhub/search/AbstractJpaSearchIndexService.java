package com.iflytek.skillhub.search;

import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentJpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared JPA-backed search document writer used by transitional runtime providers.
 */
public abstract class AbstractJpaSearchIndexService implements SearchIndexService {
    private static final int NAMESPACE_SLUG_MAX_LENGTH = 64;
    private static final int OWNER_ID_MAX_LENGTH = 128;
    private static final int TITLE_MAX_LENGTH = 512;
    private static final int VISIBILITY_MAX_LENGTH = 32;
    private static final int STATUS_MAX_LENGTH = 32;

    private final SkillSearchDocumentJpaRepository repository;
    private final SearchEmbeddingService searchEmbeddingService;

    protected AbstractJpaSearchIndexService(SkillSearchDocumentJpaRepository repository,
                                            SearchEmbeddingService searchEmbeddingService) {
        this.repository = repository;
        this.searchEmbeddingService = searchEmbeddingService;
    }

    @Override
    @Transactional
    public void index(SkillSearchDocument document) {
        SkillSearchDocument normalizedDocument = normalize(document);
        Optional<SkillSearchDocumentEntity> existing = repository.findBySkillId(document.skillId());

        if (existing.isPresent()) {
            SkillSearchDocumentEntity entity = existing.get();
            entity.setNamespaceId(normalizedDocument.namespaceId());
            entity.setNamespaceSlug(normalizedDocument.namespaceSlug());
            entity.setOwnerId(normalizedDocument.ownerId());
            entity.setTitle(normalizedDocument.title());
            entity.setSummary(normalizedDocument.summary());
            entity.setKeywords(normalizedDocument.keywords());
            entity.setSearchText(normalizedDocument.searchText());
            entity.setSemanticVector(buildSemanticVector(normalizedDocument));
            entity.setVisibility(normalizedDocument.visibility());
            entity.setStatus(normalizedDocument.status());
            repository.save(entity);
        } else {
            SkillSearchDocumentEntity entity = new SkillSearchDocumentEntity(
                    normalizedDocument.skillId(),
                    normalizedDocument.namespaceId(),
                    normalizedDocument.namespaceSlug(),
                    normalizedDocument.ownerId(),
                    normalizedDocument.title(),
                    normalizedDocument.summary(),
                    normalizedDocument.keywords(),
                    normalizedDocument.searchText(),
                    buildSemanticVector(normalizedDocument),
                    normalizedDocument.visibility(),
                    normalizedDocument.status()
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
                safe(document.summary()),
                safe(document.keywords()),
                safe(document.searchText())));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private SkillSearchDocument normalize(SkillSearchDocument document) {
        return new SkillSearchDocument(
                document.skillId(),
                document.namespaceId(),
                truncate(document.namespaceSlug(), NAMESPACE_SLUG_MAX_LENGTH),
                truncate(document.ownerId(), OWNER_ID_MAX_LENGTH),
                truncate(document.title(), TITLE_MAX_LENGTH),
                document.summary(),
                document.keywords(),
                document.searchText(),
                document.semanticVector(),
                truncate(document.visibility(), VISIBILITY_MAX_LENGTH),
                truncate(document.status(), STATUS_MAX_LENGTH),
                document.labelSlugs(),
                document.downloadCount(),
                document.ratingAvg(),
                document.updatedAtEpochMillis(),
                truncate(document.namespaceStatus(), STATUS_MAX_LENGTH),
                document.hidden()
        );
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
