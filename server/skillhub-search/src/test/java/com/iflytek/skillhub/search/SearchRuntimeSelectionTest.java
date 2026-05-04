package com.iflytek.skillhub.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.domain.label.LabelDefinitionRepository;
import com.iflytek.skillhub.domain.label.LabelTranslationRepository;
import com.iflytek.skillhub.domain.label.SkillLabelRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentJpaRepository;
import com.iflytek.skillhub.search.h2.H2LikeSearchQueryService;
import com.iflytek.skillhub.search.localfile.LocalFileIndexQueryService;
import com.iflytek.skillhub.search.localfile.LocalFileIndexRebuildService;
import com.iflytek.skillhub.search.localfile.LocalFileIndexService;
import com.iflytek.skillhub.search.mysql.MysqlNoopSearchIndexService;
import com.iflytek.skillhub.search.mysql.MysqlNoopSearchRebuildService;
import com.iflytek.skillhub.search.mysql.MysqlLikeSearchQueryService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.mockito.Mockito.mock;

class SearchRuntimeSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    TestConfig.class,
                    H2LikeSearchQueryService.class,
                    MysqlLikeSearchQueryService.class,
                    LocalFileIndexQueryService.class,
                    MysqlNoopSearchIndexService.class,
                    MysqlNoopSearchRebuildService.class,
                    LocalFileIndexService.class,
                    LocalFileIndexRebuildService.class,
                    JpaSearchIndexService.class,
                    JpaSearchRebuildService.class
            );

    @Test
    void h2SearchEngine_doesNotInstantiatePostgresOnlySearchBeans() {
        contextRunner
                .withPropertyValues("skillhub.search.engine=h2", "skillhub.search.provider=h2-like")
                .run(context -> {
                    assertThat(context).hasSingleBean(H2LikeSearchQueryService.class);
                    assertThat(context).hasSingleBean(JpaSearchIndexService.class);
                    assertThat(context).hasSingleBean(JpaSearchRebuildService.class);
                    assertThat(context).doesNotHaveBean(MysqlNoopSearchIndexService.class);
                    assertThat(context).doesNotHaveBean(MysqlNoopSearchRebuildService.class);
                    assertThat(context).doesNotHaveBean(LocalFileIndexService.class);
                    assertThat(context).doesNotHaveBean(LocalFileIndexQueryService.class);
                    assertThat(context).doesNotHaveBean(LocalFileIndexRebuildService.class);
                });
    }

    @Test
    void mysqlSearchEngine_instantiatesMysqlLikeQueryServiceWithoutH2OrLuceneBeans() {
        contextRunner
                .withPropertyValues("skillhub.search.engine=mysql", "skillhub.search.provider=mysql-like")
                .run(context -> {
                    assertThat(context).hasSingleBean(MysqlLikeSearchQueryService.class);
                    assertThat(context).doesNotHaveBean(LocalFileIndexQueryService.class);
                    assertThat(context).hasSingleBean(MysqlNoopSearchIndexService.class);
                    assertThat(context).hasSingleBean(MysqlNoopSearchRebuildService.class);
                    assertThat(context).doesNotHaveBean(H2LikeSearchQueryService.class);
                    assertThat(context).doesNotHaveBean(JpaSearchIndexService.class);
                    assertThat(context).doesNotHaveBean(JpaSearchRebuildService.class);
                    assertThat(context).doesNotHaveBean(LocalFileIndexQueryService.class);
                    assertThat(context).doesNotHaveBean(LocalFileIndexService.class);
                    assertThat(context).doesNotHaveBean(LocalFileIndexRebuildService.class);
                });
    }

    @Test
    void localFileIndexProvider_selectsLuceneIndexAndRebuildBeans() {
        contextRunner
                .withPropertyValues(
                        "skillhub.search.engine=mysql",
                        "skillhub.search.provider=local-file-index",
                        "skillhub.search.local-file-index.directory=/tmp/search-runtime-selection"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(LocalFileIndexQueryService.class);
                    assertThat(context).hasSingleBean(LocalFileIndexService.class);
                    assertThat(context).hasSingleBean(LocalFileIndexRebuildService.class);
                    assertThat(context).doesNotHaveBean(MysqlLikeSearchQueryService.class);
                    assertThat(context).doesNotHaveBean(H2LikeSearchQueryService.class);
                    assertThat(context).doesNotHaveBean(MysqlNoopSearchIndexService.class);
                    assertThat(context).doesNotHaveBean(MysqlNoopSearchRebuildService.class);
                    assertThat(context).doesNotHaveBean(JpaSearchIndexService.class);
                    assertThat(context).doesNotHaveBean(JpaSearchRebuildService.class);
                });
    }

    @Configuration
    static class TestConfig {

        @Bean
        EntityManager entityManager() {
            return mock(EntityManager.class);
        }

        @Bean
        SkillSearchDocumentJpaRepository skillSearchDocumentJpaRepository() {
            return mock(SkillSearchDocumentJpaRepository.class);
        }

        @Bean
        SkillRepository skillRepository() {
            return mock(SkillRepository.class);
        }

        @Bean
        NamespaceRepository namespaceRepository() {
            return mock(NamespaceRepository.class);
        }

        @Bean
        SkillVersionRepository skillVersionRepository() {
            return mock(SkillVersionRepository.class);
        }

        @Bean
        LabelDefinitionRepository labelDefinitionRepository() {
            return mock(LabelDefinitionRepository.class);
        }

        @Bean
        LabelTranslationRepository labelTranslationRepository() {
            return mock(LabelTranslationRepository.class);
        }

        @Bean
        SkillLabelRepository skillLabelRepository() {
            return mock(SkillLabelRepository.class);
        }

        @Bean
        SearchEmbeddingService searchEmbeddingService() {
            return mock(SearchEmbeddingService.class);
        }

        @Bean
        SearchTextTokenizer searchTextTokenizer() {
            return new SearchTextTokenizer();
        }
    }
}
