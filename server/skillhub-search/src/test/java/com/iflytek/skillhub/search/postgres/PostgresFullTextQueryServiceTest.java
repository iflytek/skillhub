package com.iflytek.skillhub.search.postgres;

import com.iflytek.skillhub.search.SearchQuery;
import com.iflytek.skillhub.search.SearchVisibilityScope;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;

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
        assertThat(sqlCaptor.getAllValues().getFirst()).contains("ts_rank_cd(search_vector, to_tsquery('simple', :tsQuery))");
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
}
