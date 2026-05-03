package com.iflytek.skillhub.search.postgres;

import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentJpaRepository;
import com.iflytek.skillhub.search.HashingSearchEmbeddingService;
import com.iflytek.skillhub.search.SearchEmbeddingService;
import com.iflytek.skillhub.search.SkillSearchDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgresFullTextIndexServiceTest {

    @Test
    void indexShouldTruncateOnlyColumnsThatStillHaveDatabaseLimits() {
        SkillSearchDocumentJpaRepository repository = mock(SkillSearchDocumentJpaRepository.class);
        when(repository.findBySkillId(1L)).thenReturn(Optional.empty());

        PostgresFullTextIndexService service = new PostgresFullTextIndexService(
                repository,
                new HashingSearchEmbeddingService()
        );

        SkillSearchDocument document = new SkillSearchDocument(
                1L,
                2L,
                "n".repeat(80),
                "o".repeat(140),
                "t".repeat(300),
                "summary",
                "k".repeat(700),
                "search text",
                null,
                "PUBLIC",
                "ACTIVE"
        );

        service.index(document);

        ArgumentCaptor<SkillSearchDocumentEntity> captor = ArgumentCaptor.forClass(SkillSearchDocumentEntity.class);
        verify(repository).save(captor.capture());

        SkillSearchDocumentEntity entity = captor.getValue();
        assertThat(entity.getNamespaceSlug()).hasSize(64);
        assertThat(entity.getOwnerId()).hasSize(128);
        assertThat(entity.getTitle()).hasSize(300);
        assertThat(entity.getKeywords()).hasSize(700);
        assertThat(entity.getSearchText()).isEqualTo("search text");
    }

    @Test
    void indexShouldUpdateExistingEntityAndBatchIndexShouldDelegatePerDocument() {
        SkillSearchDocumentJpaRepository repository = mock(SkillSearchDocumentJpaRepository.class);
        SearchEmbeddingService embeddingService = mock(SearchEmbeddingService.class);
        when(embeddingService.embed(org.mockito.ArgumentMatchers.anyString())).thenReturn("vector");

        SkillSearchDocumentEntity existing = new SkillSearchDocumentEntity(
                1L,
                2L,
                "team-ai",
                "owner-1",
                "Original title",
                "summary",
                "keywords",
                "search",
                "old-vector",
                "PUBLIC",
                "ACTIVE"
        );
        when(repository.findBySkillId(1L)).thenReturn(Optional.of(existing));
        when(repository.findBySkillId(2L)).thenReturn(Optional.empty());

        PostgresFullTextIndexService service = new PostgresFullTextIndexService(repository, embeddingService);

        service.index(new SkillSearchDocument(
                1L,
                9L,
                "updated-space",
                "owner-2",
                "Updated title",
                "updated summary",
                "updated keywords",
                "updated search",
                null,
                "NAMESPACE_ONLY",
                "ARCHIVED"
        ));

        verify(repository).save(same(existing));
        assertThat(existing.getNamespaceId()).isEqualTo(9L);
        assertThat(existing.getNamespaceSlug()).isEqualTo("updated-space");
        assertThat(existing.getOwnerId()).isEqualTo("owner-2");
        assertThat(existing.getTitle()).isEqualTo("Updated title");
        assertThat(existing.getSummary()).isEqualTo("updated summary");
        assertThat(existing.getKeywords()).isEqualTo("updated keywords");
        assertThat(existing.getSearchText()).isEqualTo("updated search");
        assertThat(existing.getSemanticVector()).isEqualTo("vector");
        assertThat(existing.getVisibility()).isEqualTo("NAMESPACE_ONLY");
        assertThat(existing.getStatus()).isEqualTo("ARCHIVED");

        service.batchIndex(List.of(
                new SkillSearchDocument(1L, 9L, "updated-space", "owner-2", "Updated title", "updated summary",
                        "updated keywords", "updated search", null, "NAMESPACE_ONLY", "ARCHIVED"),
                new SkillSearchDocument(2L, 2L, "other-space", "owner-3", "Second title", "summary",
                        "keywords", "search", null, "PUBLIC", "ACTIVE")
        ));

        verify(repository, org.mockito.Mockito.times(2)).findBySkillId(1L);
        verify(repository).findBySkillId(2L);
        verify(repository, org.mockito.Mockito.times(3)).save(org.mockito.ArgumentMatchers.any(SkillSearchDocumentEntity.class));
    }

    @Test
    void removeShouldDelegateToRepository() {
        SkillSearchDocumentJpaRepository repository = mock(SkillSearchDocumentJpaRepository.class);
        PostgresFullTextIndexService service = new PostgresFullTextIndexService(
                repository,
                new HashingSearchEmbeddingService()
        );

        service.remove(99L);

        verify(repository).deleteBySkillId(99L);
    }
}
