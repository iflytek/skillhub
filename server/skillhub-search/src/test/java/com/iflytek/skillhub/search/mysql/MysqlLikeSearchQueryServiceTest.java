package com.iflytek.skillhub.search.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.search.SearchQuery;
import com.iflytek.skillhub.search.SearchVisibilityScope;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

class MysqlLikeSearchQueryServiceTest {

    @Test
    void constructor_keepsJpaBoundaryLimitedToEntityManager() {
        EntityManager entityManager = mock(EntityManager.class);

        MysqlLikeSearchQueryService service = new MysqlLikeSearchQueryService(entityManager);

        assertThat(service.entityManager()).isSameAs(entityManager);
    }

    @Test
    void search_reportsTransitionalStatusUntilMysqlBehaviorStoriesLand() {
        MysqlLikeSearchQueryService service = new MysqlLikeSearchQueryService(mock(EntityManager.class));

        assertThatThrownBy(() -> service.search(new SearchQuery(
                "ppt",
                null,
                new SearchVisibilityScope(null, Set.of(), Set.of(), false),
                "newest",
                0,
                20,
                List.of()
        )))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("mysql-like search behavior is not implemented yet");
    }
}
