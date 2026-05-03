package com.iflytek.skillhub.search.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.search.SearchQuery;
import com.iflytek.skillhub.search.SearchResult;
import com.iflytek.skillhub.search.SearchVisibilityScope;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MysqlLikeSearchQueryServiceTest {

    @Test
    void search_shouldApplyNamespaceLabelAndVisibilityFilters() {
        EntityManager entityManager = mock(EntityManager.class);
        Query resultQuery = mock(Query.class);
        Query countQuery = mock(Query.class);

        when(entityManager.createNativeQuery(anyString())).thenReturn(resultQuery, countQuery);
        when(resultQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(resultQuery);
        when(resultQuery.getResultList()).thenReturn(List.of(11L, 10L));
        when(countQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(2L);

        MysqlLikeSearchQueryService service = new MysqlLikeSearchQueryService(entityManager);

        SearchResult result = service.search(new SearchQuery(
                null,
                7L,
                new SearchVisibilityScope("user-9", Set.of(7L), Set.of(), false),
                "newest",
                1,
                20,
                List.of("official", "code-generation")
        ));

        assertThat(result.skillIds()).containsExactly(11L, 10L);
        assertThat(result.total()).isEqualTo(2L);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.times(2)).createNativeQuery(queryCaptor.capture());
        assertThat(queryCaptor.getAllValues().get(0)).contains("d.namespace_id = :namespaceId");
        assertThat(queryCaptor.getAllValues().get(0)).contains("LOWER(ld.slug) IN (:labelSlugs)");
        assertThat(queryCaptor.getAllValues().get(0)).contains("d.visibility = 'PUBLIC'");
        assertThat(queryCaptor.getAllValues().get(0)).contains("OR (d.visibility = 'NAMESPACE_ONLY' AND d.namespace_id IN (:memberNamespaceIds))");

        verify(resultQuery).setParameter("namespaceId", 7L);
        verify(resultQuery).setParameter("memberNamespaceIds", Set.of(7L));
        verify(resultQuery).setParameter("labelSlugs", List.of("official", "code-generation"));
        verify(resultQuery).setParameter("limit", 20);
        verify(resultQuery).setParameter("offset", 20);
        verify(countQuery).setParameter("namespaceId", 7L);
        verify(countQuery).setParameter("memberNamespaceIds", Set.of(7L));
        verify(countQuery).setParameter("labelSlugs", List.of("official", "code-generation"));
    }

    @Test
    void search_shouldNotBindMemberNamespacesForAnonymousUsers() {
        EntityManager entityManager = mock(EntityManager.class);
        Query resultQuery = mock(Query.class);
        Query countQuery = mock(Query.class);

        when(entityManager.createNativeQuery(anyString())).thenReturn(resultQuery, countQuery);
        when(resultQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(resultQuery);
        when(resultQuery.getResultList()).thenReturn(List.of());
        when(countQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(0L);

        MysqlLikeSearchQueryService service = new MysqlLikeSearchQueryService(entityManager);

        service.search(new SearchQuery(
                null,
                null,
                SearchVisibilityScope.anonymous(),
                "newest",
                0,
                12,
                List.of()
        ));

        verify(resultQuery, never()).setParameter("memberNamespaceIds", Set.of(-1L));
        verify(countQuery, never()).setParameter("memberNamespaceIds", Set.of(-1L));
        verify(resultQuery).setParameter("skillStatusActive", com.iflytek.skillhub.domain.skill.SkillStatus.ACTIVE);
        verify(resultQuery).setParameter("namespaceStatusArchived", com.iflytek.skillhub.domain.namespace.NamespaceStatus.ARCHIVED);
    }

    @Test
    void search_shouldMatchKeywordAgainstTitleSummaryKeywordsAndSearchText() {
        EntityManager entityManager = mock(EntityManager.class);
        Query resultQuery = mock(Query.class);
        Query countQuery = mock(Query.class);

        when(entityManager.createNativeQuery(anyString())).thenReturn(resultQuery, countQuery);
        when(resultQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(resultQuery);
        when(resultQuery.getResultList()).thenReturn(List.of(9L));
        when(countQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(1L);

        MysqlLikeSearchQueryService service = new MysqlLikeSearchQueryService(entityManager);

        SearchResult result = service.search(new SearchQuery(
                "  PPT  ",
                null,
                SearchVisibilityScope.anonymous(),
                "newest",
                0,
                12,
                List.of()
        ));

        assertThat(result.skillIds()).containsExactly(9L);
        assertThat(result.total()).isEqualTo(1L);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.times(2)).createNativeQuery(queryCaptor.capture());
        assertThat(queryCaptor.getAllValues().get(0)).contains("LOWER(COALESCE(d.title, '')) LIKE :titleLike");
        assertThat(queryCaptor.getAllValues().get(0)).contains("LOWER(COALESCE(d.summary, '')) LIKE :titleLike");
        assertThat(queryCaptor.getAllValues().get(0)).contains("LOWER(COALESCE(d.keywords, '')) LIKE :titleLike");
        assertThat(queryCaptor.getAllValues().get(0)).contains("LOWER(COALESCE(d.search_text, '')) LIKE :titleLike");
        verify(resultQuery).setParameter("titleLike", "%ppt%");
        verify(countQuery).setParameter("titleLike", "%ppt%");
    }

    @Test
    void search_shouldTreatWhitespaceOnlyKeywordAsEmptyQuery() {
        EntityManager entityManager = mock(EntityManager.class);
        Query resultQuery = mock(Query.class);
        Query countQuery = mock(Query.class);

        when(entityManager.createNativeQuery(anyString())).thenReturn(resultQuery, countQuery);
        when(resultQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(resultQuery);
        when(resultQuery.getResultList()).thenReturn(List.of());
        when(countQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(0L);

        MysqlLikeSearchQueryService service = new MysqlLikeSearchQueryService(entityManager);

        service.search(new SearchQuery(
                "   ",
                null,
                SearchVisibilityScope.anonymous(),
                "newest",
                0,
                12,
                List.of()
        ));

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.times(2)).createNativeQuery(queryCaptor.capture());
        assertThat(queryCaptor.getAllValues().get(0)).doesNotContain("LOWER(COALESCE(d.title, '')) LIKE :titleLike");
        assertThat(queryCaptor.getAllValues().get(0)).doesNotContain("LOWER(COALESCE(d.search_text, '')) LIKE :titleLike");
        verify(resultQuery, never()).setParameter("titleLike", "% %");
        verify(resultQuery, never()).setParameter("titleLike", "%%");
        verify(countQuery, never()).setParameter("titleLike", "% %");
        verify(countQuery, never()).setParameter("titleLike", "%%");
    }

    @Test
    void search_shouldOrderByDownloadsAndApplyPaginationOffset() {
        EntityManager entityManager = mock(EntityManager.class);
        Query resultQuery = mock(Query.class);
        Query countQuery = mock(Query.class);

        when(entityManager.createNativeQuery(anyString())).thenReturn(resultQuery, countQuery);
        when(resultQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(resultQuery);
        when(resultQuery.getResultList()).thenReturn(List.of(3L, 2L));
        when(countQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(2L);

        MysqlLikeSearchQueryService service = new MysqlLikeSearchQueryService(entityManager);

        SearchResult result = service.search(new SearchQuery(
                null,
                null,
                SearchVisibilityScope.anonymous(),
                "downloads",
                2,
                20,
                List.of()
        ));

        assertThat(result.skillIds()).containsExactly(3L, 2L);
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.times(2)).createNativeQuery(queryCaptor.capture());
        assertThat(queryCaptor.getAllValues().get(0)).contains("ORDER BY s.download_count DESC, s.updated_at DESC, d.skill_id DESC");
        verify(resultQuery).setParameter("limit", 20);
        verify(resultQuery).setParameter("offset", 40);
    }

    @Test
    void search_shouldOrderByRatingForRatingSort() {
        EntityManager entityManager = mock(EntityManager.class);
        Query resultQuery = mock(Query.class);
        Query countQuery = mock(Query.class);

        when(entityManager.createNativeQuery(anyString())).thenReturn(resultQuery, countQuery);
        when(resultQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(resultQuery);
        when(resultQuery.getResultList()).thenReturn(List.of(8L));
        when(countQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(1L);

        MysqlLikeSearchQueryService service = new MysqlLikeSearchQueryService(entityManager);

        service.search(new SearchQuery(
                null,
                null,
                SearchVisibilityScope.anonymous(),
                "rating",
                0,
                10,
                List.of()
        ));

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.times(2)).createNativeQuery(queryCaptor.capture());
        assertThat(queryCaptor.getAllValues().get(0)).contains("ORDER BY s.rating_avg DESC, s.updated_at DESC, d.skill_id DESC");
    }

    @Test
    void search_shouldUseRelevanceRankingWhenKeywordAndSortAreRelevance() {
        EntityManager entityManager = mock(EntityManager.class);
        Query resultQuery = mock(Query.class);
        Query countQuery = mock(Query.class);

        when(entityManager.createNativeQuery(anyString())).thenReturn(resultQuery, countQuery);
        when(resultQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(resultQuery);
        when(resultQuery.getResultList()).thenReturn(List.of(42L));
        when(countQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(1L);

        MysqlLikeSearchQueryService service = new MysqlLikeSearchQueryService(entityManager);

        service.search(new SearchQuery(
                "Agent",
                null,
                SearchVisibilityScope.anonymous(),
                "relevance",
                0,
                10,
                List.of()
        ));

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.times(2)).createNativeQuery(queryCaptor.capture());
        assertThat(queryCaptor.getAllValues().get(0)).contains("WHEN LOWER(COALESCE(d.title, '')) = :titleExact THEN 4");
        assertThat(queryCaptor.getAllValues().get(0)).contains("WHEN LOWER(COALESCE(d.title, '')) LIKE :titlePrefix THEN 3");
        verify(resultQuery).setParameter("titleExact", "agent");
        verify(resultQuery).setParameter("titlePrefix", "agent%");
        verify(countQuery, never()).setParameter("titleExact", "agent");
        verify(countQuery, never()).setParameter("titlePrefix", "agent%");
    }
}
