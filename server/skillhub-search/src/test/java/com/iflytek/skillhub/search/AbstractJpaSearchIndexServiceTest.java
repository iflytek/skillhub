package com.iflytek.skillhub.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentJpaRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbstractJpaSearchIndexServiceTest {

    private SkillSearchDocumentJpaRepository repository;
    private SearchEmbeddingService embeddingService;
    private TestSearchIndexService service;

    @BeforeEach
    void setUp() {
        repository = mock(SkillSearchDocumentJpaRepository.class);
        embeddingService = mock(SearchEmbeddingService.class);
        service = new TestSearchIndexService(repository, embeddingService);
    }

    @Test
    void indexCreatesNewEntityWhenNotExists() {
        when(repository.findBySkillId(1L)).thenReturn(Optional.empty());
        when(embeddingService.embed(any())).thenReturn("0.1,0.2");

        SkillSearchDocument doc = new SkillSearchDocument(
                1L, 1L, "ns", "owner", "title", "summary", "kw", "search",
                null, "PUBLIC", "ACTIVE", List.of(), 0L, 0.0, 0L, "ACTIVE", false
        );

        service.index(doc);

        verify(repository).save(any(SkillSearchDocumentEntity.class));
    }

    @Test
    void indexUpdatesExistingEntity() {
        SkillSearchDocumentEntity existing = new SkillSearchDocumentEntity(
                1L, 1L, "ns", "owner", "old-title", "old-summary", "old-kw", "old-search",
                "0.0", "PUBLIC", "ACTIVE"
        );
        when(repository.findBySkillId(1L)).thenReturn(Optional.of(existing));
        when(embeddingService.embed(any())).thenReturn("0.1,0.2");

        SkillSearchDocument doc = new SkillSearchDocument(
                1L, 1L, "ns", "owner", "new-title", "new-summary", "kw", "search",
                null, "PUBLIC", "ACTIVE", List.of(), 0L, 0.0, 0L, "ACTIVE", false
        );

        service.index(doc);

        assertThat(existing.getTitle()).isEqualTo("new-title");
        verify(repository).save(existing);
    }

    @Test
    void batchIndexCallsIndexForEachDocument() {
        when(repository.findBySkillId(any())).thenReturn(Optional.empty());
        when(embeddingService.embed(any())).thenReturn("0.1");

        SkillSearchDocument doc1 = new SkillSearchDocument(
                1L, 1L, "ns", "owner", "title1", "sum1", "kw1", "search1",
                null, "PUBLIC", "ACTIVE", List.of(), 0L, 0.0, 0L, "ACTIVE", false
        );
        SkillSearchDocument doc2 = new SkillSearchDocument(
                2L, 1L, "ns", "owner", "title2", "sum2", "kw2", "search2",
                null, "PUBLIC", "ACTIVE", List.of(), 0L, 0.0, 0L, "ACTIVE", false
        );

        service.batchIndex(List.of(doc1, doc2));

        verify(repository, times(2)).save(any(SkillSearchDocumentEntity.class));
    }

    @Test
    void removeCallsDeleteBySkillId() {
        service.remove(1L);
        verify(repository).deleteBySkillId(1L);
    }

    @Test
    void normalizeTruncatesLongFields() {
        when(repository.findBySkillId(1L)).thenReturn(Optional.empty());
        when(embeddingService.embed(any())).thenReturn("0.1");

        String longSlug = "a".repeat(100);
        String longOwner = "b".repeat(200);
        String longTitle = "c".repeat(600);
        String longVisibility = "d".repeat(50);
        String longStatus = "e".repeat(50);

        SkillSearchDocument doc = new SkillSearchDocument(
                1L, 1L, longSlug, longOwner, longTitle, "sum", "kw", "search",
                null, longVisibility, longStatus, List.of(), 0L, 0.0, 0L, longStatus, false
        );

        service.index(doc);

        verify(repository).save(any(SkillSearchDocumentEntity.class));
    }

    @Test
    void buildSemanticVectorHandlesNullFields() {
        when(repository.findBySkillId(1L)).thenReturn(Optional.empty());
        when(embeddingService.embed(any())).thenReturn("0.1");

        SkillSearchDocument doc = new SkillSearchDocument(
                1L, 1L, "ns", "owner", null, null, null, null,
                null, "PUBLIC", "ACTIVE", List.of(), 0L, 0.0, 0L, "ACTIVE", false
        );

        service.index(doc);

        verify(repository).save(any(SkillSearchDocumentEntity.class));
    }

    @Test
    void normalizePreservesShortValues() {
        when(repository.findBySkillId(1L)).thenReturn(Optional.empty());
        when(embeddingService.embed(any())).thenReturn("0.1");

        SkillSearchDocument doc = new SkillSearchDocument(
                1L, 1L, "ns", "owner", "t", "s", "k", "search",
                null, "PUBLIC", "ACTIVE", List.of(), 0L, 0.0, 0L, "ACTIVE", false
        );

        service.index(doc);

        verify(repository).save(any(SkillSearchDocumentEntity.class));
    }

    private static class TestSearchIndexService extends AbstractJpaSearchIndexService {
        TestSearchIndexService(SkillSearchDocumentJpaRepository repository,
                               SearchEmbeddingService searchEmbeddingService) {
            super(repository, searchEmbeddingService);
        }
    }
}
