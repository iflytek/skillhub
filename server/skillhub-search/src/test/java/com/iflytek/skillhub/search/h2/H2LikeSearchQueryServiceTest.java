package com.iflytek.skillhub.search.h2;

import com.iflytek.skillhub.search.SearchQuery;
import com.iflytek.skillhub.search.SearchVisibilityScope;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class H2LikeSearchQueryServiceTest {

    @Test
    void search_shouldUseEntityPropertyNameForSearchTextInKeywordQueries() {
        EntityManager entityManager = mock(EntityManager.class);
        TypedQuery<Long> resultQuery = mock(TypedQuery.class);
        TypedQuery<Long> countQuery = mock(TypedQuery.class);

        when(entityManager.createQuery(anyString(), eq(Long.class)))
                .thenReturn(resultQuery, countQuery);
        when(resultQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(resultQuery);
        when(resultQuery.setFirstResult(0)).thenReturn(resultQuery);
        when(resultQuery.setMaxResults(12)).thenReturn(resultQuery);
        when(resultQuery.getResultList()).thenReturn(List.of());
        when(countQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(0L);

        H2LikeSearchQueryService service = new H2LikeSearchQueryService(entityManager);

        service.search(new SearchQuery(
                "ppt",
                null,
                new SearchVisibilityScope(null, Set.of(), Set.of(), false),
                "newest",
                0,
                12,
                List.of()
        ));

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.times(2)).createQuery(queryCaptor.capture(), eq(Long.class));
        assertThat(queryCaptor.getAllValues().get(0)).contains("d.searchText");
        assertThat(queryCaptor.getAllValues().get(0)).doesNotContain("d.search_text");
        verify(resultQuery, never()).setParameter("titleExact", "ppt");
        verify(resultQuery, never()).setParameter("titlePrefix", "ppt%");
    }

    @Test
    void search_shouldNotBindRelevanceOnlyParametersToCountQuery() {
        EntityManager entityManager = mock(EntityManager.class);
        TypedQuery<Long> resultQuery = mock(TypedQuery.class);
        TypedQuery<Long> countQuery = mock(TypedQuery.class);

        when(entityManager.createQuery(anyString(), eq(Long.class)))
                .thenReturn(resultQuery, countQuery);
        when(resultQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(resultQuery);
        when(resultQuery.setFirstResult(0)).thenReturn(resultQuery);
        when(resultQuery.setMaxResults(12)).thenReturn(resultQuery);
        when(resultQuery.getResultList()).thenReturn(List.of());
        when(countQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(0L);

        H2LikeSearchQueryService service = new H2LikeSearchQueryService(entityManager);

        service.search(new SearchQuery(
                "ppt",
                null,
                new SearchVisibilityScope(null, Set.of(), Set.of(), false),
                "relevance",
                0,
                12,
                List.of()
        ));

        verify(resultQuery).setParameter("titleExact", "ppt");
        verify(resultQuery).setParameter("titlePrefix", "ppt%");
        verify(countQuery, never()).setParameter("titleExact", "ppt");
        verify(countQuery, never()).setParameter("titlePrefix", "ppt%");
    }
}
