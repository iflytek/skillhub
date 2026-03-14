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
    void shortKeywordsShouldUseLikeFallback() {
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

        verify(nativeQuery).setParameter("keywordLike", "%ai%");
        verify(countQuery).setParameter("keywordLike", "%ai%");
        verify(nativeQuery, never()).setParameter("keyword", "ai");
        verify(countQuery, never()).setParameter("keyword", "ai");
    }

    @Test
    void longerKeywordsShouldKeepFullTextSearch() {
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

        verify(nativeQuery).setParameter("keyword", "agent");
        verify(countQuery).setParameter("keyword", "agent");
        verify(nativeQuery, never()).setParameter("keywordLike", "%agent%");
        verify(countQuery, never()).setParameter("keywordLike", "%agent%");
    }

    @Test
    void shortKeywordSqlShouldAvoidTsRankOrdering() {
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
                "go",
                null,
                new SearchVisibilityScope(null, Set.of(), Set.of()),
                "relevance",
                0,
                20
        ));

        var sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.times(2)).createNativeQuery(sqlCaptor.capture());
        assertThat(sqlCaptor.getAllValues().getFirst()).contains("LOWER(title) LIKE LOWER(:keywordLike)");
        assertThat(sqlCaptor.getAllValues().getFirst()).doesNotContain("ts_rank");
    }
}
