package com.iflytek.skillhub.search.postgres;

import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentJpaRepository;
import com.iflytek.skillhub.search.HashingSearchEmbeddingService;
import com.iflytek.skillhub.search.SearchQuery;
import com.iflytek.skillhub.search.SearchVisibilityScope;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgresFullTextQueryServiceTest {

    @Test
    void shortKeywordsShouldUsePrefixTsQuery() {
        EntityManager entityManager = mock(EntityManager.class);
        Query nativeQuery = mock(Query.class);
        Query countQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString()))
                .thenReturn(nativeQuery)
                .thenReturn(countQuery);
        when(nativeQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(nativeQuery);
        when(countQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(countQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of(1L));
        when(countQuery.getSingleResult()).thenReturn(1L);

        PostgresFullTextQueryService service = new PostgresFullTextQueryService(entityManager);

        service.search(new SearchQuery(
                "ai",
                null,
                new SearchVisibilityScope(null, Set.of(), Set.of()),
                "relevance",
                0,
                20
        ));

        verify(nativeQuery).setParameter("tsQuery", "ai:*");
        verify(countQuery).setParameter("tsQuery", "ai:*");
        var sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.times(2)).createNativeQuery(sqlCaptor.capture());
        assertThat(sqlCaptor.getAllValues().getFirst()).contains("to_tsvector('simple', coalesce(title, '')) @@ to_tsquery('simple', :tsQuery)");
        assertThat(sqlCaptor.getAllValues().getFirst()).contains("LOWER(title) LIKE :titleLike");
    }

    @Test
    void longerKeywordsShouldUsePrefixTsQuery() {
        EntityManager entityManager = mock(EntityManager.class);
        Query nativeQuery = mock(Query.class);
        Query countQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString()))
                .thenReturn(nativeQuery)
                .thenReturn(countQuery);
        when(nativeQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(nativeQuery);
        when(countQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(countQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of(1L));
        when(countQuery.getSingleResult()).thenReturn(1L);

        PostgresFullTextQueryService service = new PostgresFullTextQueryService(entityManager);

        service.search(new SearchQuery(
                "agent",
                null,
                new SearchVisibilityScope(null, Set.of(), Set.of()),
                "relevance",
                0,
                20
        ));

        verify(nativeQuery).setParameter("tsQuery", "agent:*");
        verify(countQuery).setParameter("tsQuery", "agent:*");
    }

    @Test
    void prefixSearchSqlShouldUseVectorRanking() {
        EntityManager entityManager = mock(EntityManager.class);
        Query nativeQuery = mock(Query.class);
        Query countQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString()))
                .thenReturn(nativeQuery)
                .thenReturn(countQuery);
        when(nativeQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(nativeQuery);
        when(countQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(countQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of());
        when(countQuery.getSingleResult()).thenReturn(0L);

        PostgresFullTextQueryService service = new PostgresFullTextQueryService(entityManager);

        service.search(new SearchQuery(
                "sel",
                null,
                new SearchVisibilityScope(null, Set.of(), Set.of()),
                "relevance",
                0,
                20
        ));

        var sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.times(2)).createNativeQuery(sqlCaptor.capture());
        assertThat(sqlCaptor.getAllValues().getFirst()).contains("search_vector @@ to_tsquery('simple', :tsQuery)");
        assertThat(sqlCaptor.getAllValues().getFirst()).contains("ts_rank_cd(d.search_vector, to_tsquery('simple', :tsQuery))");
    }

    @Test
    void shortPrefixRelevanceShouldRankUsingTitleVectorWithoutDuplicateOrderBy() {
        EntityManager entityManager = mock(EntityManager.class);
        Query nativeQuery = mock(Query.class);
        Query countQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString()))
                .thenReturn(nativeQuery)
                .thenReturn(countQuery);
        when(nativeQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(nativeQuery);
        when(countQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(countQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of());
        when(countQuery.getSingleResult()).thenReturn(0L);

        PostgresFullTextQueryService service = new PostgresFullTextQueryService(entityManager);

        service.search(new SearchQuery(
                "x",
                null,
                new SearchVisibilityScope(null, Set.of(), Set.of()),
                "relevance",
                0,
                20
        ));

        var sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.times(2)).createNativeQuery(sqlCaptor.capture());
        assertThat(sqlCaptor.getAllValues().getFirst()).contains("ts_rank_cd(to_tsvector('simple', coalesce(title, '')), to_tsquery('simple', :tsQuery))");
        assertThat(sqlCaptor.getAllValues().getFirst()).doesNotContain("ORDER BY ORDER BY");
    }

    @Test
    void multipleTermsShouldBuildPrefixQueryForEachLexeme() {
        EntityManager entityManager = mock(EntityManager.class);
        Query nativeQuery = mock(Query.class);
        Query countQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString()))
                .thenReturn(nativeQuery)
                .thenReturn(countQuery);
        when(nativeQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(nativeQuery);
        when(countQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(countQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of());
        when(countQuery.getSingleResult()).thenReturn(0L);

        PostgresFullTextQueryService service = new PostgresFullTextQueryService(entityManager);

        service.search(new SearchQuery(
                "self improving",
                null,
                new SearchVisibilityScope(null, Set.of(), Set.of()),
                "relevance",
                0,
                20
        ));

        verify(nativeQuery).setParameter("tsQuery", "self:* & improving:*");
        verify(countQuery).setParameter("tsQuery", "self:* & improving:*");
    }

    @Test
    void numericKeywordsShouldFallbackToTitleLikeSearchWithoutTsQuery() {
        EntityManager entityManager = mock(EntityManager.class);
        Query nativeQuery = mock(Query.class);
        Query countQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString()))
                .thenReturn(nativeQuery)
                .thenReturn(countQuery);
        when(nativeQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(nativeQuery);
        when(countQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(countQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of());
        when(countQuery.getSingleResult()).thenReturn(0L);

        PostgresFullTextQueryService service = new PostgresFullTextQueryService(entityManager);

        service.search(new SearchQuery(
                "122222222222222222222222",
                null,
                new SearchVisibilityScope(null, Set.of(), Set.of()),
                "relevance",
                0,
                20
        ));

        verify(nativeQuery, never()).setParameter(org.mockito.ArgumentMatchers.eq("tsQuery"), anyString());
        verify(countQuery, never()).setParameter(org.mockito.ArgumentMatchers.eq("tsQuery"), anyString());

        var sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.times(2)).createNativeQuery(sqlCaptor.capture());
        assertThat(sqlCaptor.getAllValues().getFirst()).doesNotContain("to_tsquery('simple', :tsQuery)");
        assertThat(sqlCaptor.getAllValues().getFirst()).contains("LOWER(title) LIKE :titleLike");
    }

    @Test
    void downloadsSortShouldNotBindRelevanceOnlyParameters() {
        EntityManager entityManager = mock(EntityManager.class);
        Query nativeQuery = mock(Query.class);
        Query countQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString()))
                .thenReturn(nativeQuery)
                .thenReturn(countQuery);
        when(nativeQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(nativeQuery);
        when(countQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(countQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of());
        when(countQuery.getSingleResult()).thenReturn(0L);

        PostgresFullTextQueryService service = new PostgresFullTextQueryService(entityManager);

        service.search(new SearchQuery(
                "51222222333",
                null,
                new SearchVisibilityScope(null, Set.of(), Set.of()),
                "downloads",
                0,
                12
        ));

        verify(nativeQuery, never()).setParameter(org.mockito.ArgumentMatchers.eq("titleExact"), anyString());
        verify(nativeQuery, never()).setParameter(org.mockito.ArgumentMatchers.eq("titlePrefix"), anyString());
        verify(nativeQuery).setParameter("titleLike", "%51222222333%");

        var sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.times(2)).createNativeQuery(sqlCaptor.capture());
        assertThat(sqlCaptor.getAllValues().getFirst())
                .contains("JOIN skill s ON s.id = d.skill_id")
                .contains("JOIN namespace n ON n.id = d.namespace_id")
                .contains("AND s.hidden = FALSE")
                .contains("AND (n.status <> 'ARCHIVED' ")
                .contains("ORDER BY s.download_count DESC, s.updated_at DESC, d.skill_id DESC");
    }

    @Test
    void emptyKeywordRelevanceShouldUseStableNewestOrdering() {
        EntityManager entityManager = mock(EntityManager.class);
        Query nativeQuery = mock(Query.class);
        Query countQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString()))
                .thenReturn(nativeQuery)
                .thenReturn(countQuery);
        when(nativeQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(nativeQuery);
        when(countQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(countQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of());
        when(countQuery.getSingleResult()).thenReturn(0L);

        PostgresFullTextQueryService service = new PostgresFullTextQueryService(entityManager);

        service.search(new SearchQuery(
                null,
                null,
                new SearchVisibilityScope(null, Set.of(), Set.of()),
                "relevance",
                0,
                12
        ));

        var sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.times(2)).createNativeQuery(sqlCaptor.capture());
        assertThat(sqlCaptor.getAllValues().getFirst())
                .contains("ORDER BY s.updated_at DESC, d.skill_id DESC");
    }

    @Test
    void authenticatedQueriesShouldAllowArchivedNamespacesForMembers() {
        EntityManager entityManager = mock(EntityManager.class);
        Query nativeQuery = mock(Query.class);
        Query countQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString()))
                .thenReturn(nativeQuery)
                .thenReturn(countQuery);
        when(nativeQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(nativeQuery);
        when(countQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(countQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of());
        when(countQuery.getSingleResult()).thenReturn(0L);

        PostgresFullTextQueryService service = new PostgresFullTextQueryService(entityManager);

        service.search(new SearchQuery(
                null,
                null,
                new SearchVisibilityScope("user-1", Set.of(7L), Set.of()),
                "newest",
                0,
                12
        ));

        var sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.times(2)).createNativeQuery(sqlCaptor.capture());
        assertThat(sqlCaptor.getAllValues().getFirst()).contains("OR d.namespace_id IN :memberNamespaceIds");
    }

    @Test
    void maliciousKeywordShouldBeBoundAsParameterInsteadOfInlinedIntoSql() {
        EntityManager entityManager = mock(EntityManager.class);
        Query nativeQuery = mock(Query.class);
        Query countQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString()))
                .thenReturn(nativeQuery)
                .thenReturn(countQuery);
        when(nativeQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(nativeQuery);
        when(countQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(countQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of());
        when(countQuery.getSingleResult()).thenReturn(0L);

        PostgresFullTextQueryService service = new PostgresFullTextQueryService(entityManager);
        String payload = "x%' OR 1=1 --";

        service.search(new SearchQuery(
                payload,
                null,
                new SearchVisibilityScope(null, Set.of(), Set.of()),
                "relevance",
                0,
                12
        ));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.times(2)).createNativeQuery(sqlCaptor.capture());
        assertThat(sqlCaptor.getAllValues().getFirst()).doesNotContain(payload);
        verify(nativeQuery).setParameter("titleLike", "%" + payload.toLowerCase() + "%");
        verify(countQuery).setParameter("titleLike", "%" + payload.toLowerCase() + "%");
    }

    @Test
    void maliciousSortShouldFallBackWithoutBeingInlinedIntoSql() {
        EntityManager entityManager = mock(EntityManager.class);
        Query nativeQuery = mock(Query.class);
        Query countQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString()))
                .thenReturn(nativeQuery)
                .thenReturn(countQuery);
        when(nativeQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(nativeQuery);
        when(countQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(countQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of());
        when(countQuery.getSingleResult()).thenReturn(0L);

        PostgresFullTextQueryService service = new PostgresFullTextQueryService(entityManager);
        String payload = "newest desc; drop table skill; --";

        service.search(new SearchQuery(
                null,
                null,
                new SearchVisibilityScope(null, Set.of(), Set.of()),
                payload,
                0,
                12
        ));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.times(2)).createNativeQuery(sqlCaptor.capture());
        assertThat(sqlCaptor.getAllValues().getFirst())
                .doesNotContain(payload)
                .contains("ORDER BY s.updated_at DESC, d.skill_id DESC");
    }

    @Test
    void semanticRerankShouldPromoteSemanticallyRelevantCandidate() {
        EntityManager entityManager = mock(EntityManager.class);
        Query nativeQuery = mock(Query.class);
        Query countQuery = mock(Query.class);
        SkillSearchDocumentJpaRepository repository = mock(SkillSearchDocumentJpaRepository.class);
        HashingSearchEmbeddingService embeddingService = new HashingSearchEmbeddingService();
        when(entityManager.createNativeQuery(anyString()))
                .thenReturn(nativeQuery)
                .thenReturn(countQuery);
        when(nativeQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(nativeQuery);
        when(countQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(countQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of(2L, 1L));
        when(countQuery.getSingleResult()).thenReturn(2L);
        when(repository.findBySkillIdIn(List.of(2L, 1L))).thenReturn(List.of(
                new SkillSearchDocumentEntity(1L, 1L, "global", "user-1", "Self Improvement Coach",
                        "Build better habits", "habits,self improvement", "habit tracker and self improvement guide",
                        embeddingService.embed("habit tracker and self improvement guide"), "PUBLIC", "ACTIVE"),
                new SkillSearchDocumentEntity(2L, 1L, "global", "user-2", "Web Search Exa",
                        "Research assistant", "keywords,search", "web search keywords company research",
                        embeddingService.embed("web search keywords company research"), "PUBLIC", "ACTIVE")
        ));

        PostgresFullTextQueryService service = new PostgresFullTextQueryService(
                entityManager,
                repository,
                embeddingService,
                true,
                0.6D,
                8,
                120
        );

        var result = service.search(new SearchQuery(
                "self improvement",
                null,
                new SearchVisibilityScope(null, Set.of(), Set.of()),
                "relevance",
                0,
                2
        ));

        verify(nativeQuery).setParameter("limit", 16);
        verify(nativeQuery).setParameter("offset", 0);
        assertThat(result.skillIds()).containsExactly(1L, 2L);
    }

    @Test
    void deepSemanticPagesShouldFallBackToDatabasePagination() {
        EntityManager entityManager = mock(EntityManager.class);
        Query nativeQuery = mock(Query.class);
        Query countQuery = mock(Query.class);
        SkillSearchDocumentJpaRepository repository = mock(SkillSearchDocumentJpaRepository.class);
        HashingSearchEmbeddingService embeddingService = new HashingSearchEmbeddingService();
        when(entityManager.createNativeQuery(anyString()))
                .thenReturn(nativeQuery)
                .thenReturn(countQuery);
        when(nativeQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(nativeQuery);
        when(countQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(countQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of(201L, 202L));
        when(countQuery.getSingleResult()).thenReturn(1000L);

        PostgresFullTextQueryService service = new PostgresFullTextQueryService(
                entityManager,
                repository,
                embeddingService,
                true,
                0.6D,
                8,
                120
        );

        var result = service.search(new SearchQuery(
                "self improvement",
                null,
                new SearchVisibilityScope(null, Set.of(), Set.of()),
                "relevance",
                20,
                10
        ));

        verify(nativeQuery).setParameter("limit", 10);
        verify(nativeQuery).setParameter("offset", 200);
        verify(repository, never()).findBySkillIdIn(org.mockito.ArgumentMatchers.anyList());
        assertThat(result.skillIds()).containsExactly(201L, 202L);
        assertThat(result.total()).isEqualTo(1000L);
    }
}
