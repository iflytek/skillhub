package com.iflytek.skillhub.search.localfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.search.SearchQuery;
import com.iflytek.skillhub.search.SearchResult;
import com.iflytek.skillhub.search.SearchVisibilityScope;
import com.iflytek.skillhub.search.SkillSearchDocument;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileIndexQueryServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void search_shouldMatchKeywordAcrossLuceneDocumentFieldsAndReturnSkillIds() {
        LocalFileIndexService indexService = new LocalFileIndexService(tempDir);
        LocalFileIndexQueryService queryService = new LocalFileIndexQueryService(tempDir);
        indexService.batchIndex(List.of(
                document(11L, 10L, "global", "PUBLIC", "ACTIVE", "ACTIVE", false,
                        "Prompt Agent", "Builds workflows", "automation", "multi step pipelines",
                        List.of("official"), 5L, 4.1D, 100L),
                document(22L, 10L, "global", "PUBLIC", "ACTIVE", "ACTIVE", false,
                        "Search Helper", "Finds indexed content", "discovery", "lucene agent gateway",
                        List.of("search"), 7L, 4.3D, 90L),
                document(33L, 10L, "global", "PUBLIC", "ACTIVE", "ACTIVE", false,
                        "Billing Tool", "Finance utility", "ledger", "invoice export",
                        List.of("finance"), 1L, 3.5D, 80L)
        ));

        SearchResult result = queryService.search(new SearchQuery(
                "  agent  ",
                null,
                SearchVisibilityScope.anonymous(),
                "relevance",
                0,
                10,
                List.of()
        ));

        assertThat(result.skillIds()).containsExactly(11L, 22L);
        assertThat(result.total()).isEqualTo(2L);
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(10);
    }

    @Test
    void search_shouldFilterByNamespaceLabelsAndVisibility() {
        LocalFileIndexService indexService = new LocalFileIndexService(tempDir);
        LocalFileIndexQueryService queryService = new LocalFileIndexQueryService(tempDir);
        indexService.batchIndex(List.of(
                document(101L, 10L, "global", "PUBLIC", "ACTIVE", "ACTIVE", false,
                        "Global Agent", "Public", "agent", "global search",
                        List.of("official", "beta"), 10L, 4.8D, 200L),
                document(202L, 20L, "team-ai", "NAMESPACE_ONLY", "ACTIVE", "ACTIVE", false,
                        "Team Agent", "Member only", "agent", "team search",
                        List.of("official"), 12L, 4.7D, 190L),
                document(303L, 20L, "team-ai", "NAMESPACE_ONLY", "ACTIVE", "ACTIVE", false,
                        "Wrong Label", "Member only", "agent", "team search",
                        List.of("internal"), 8L, 4.5D, 180L),
                document(404L, 30L, "archived-space", "PUBLIC", "ACTIVE", "ARCHIVED", false,
                        "Archived Namespace", "Should hide", "agent", "archived namespace",
                        List.of("official"), 20L, 4.9D, 210L),
                document(505L, 10L, "global", "PUBLIC", "ACTIVE", "ACTIVE", true,
                        "Hidden Agent", "Should hide", "agent", "hidden",
                        List.of("official"), 30L, 4.2D, 220L)
        ));

        SearchResult anonymousResult = queryService.search(new SearchQuery(
                "agent",
                20L,
                SearchVisibilityScope.anonymous(),
                "relevance",
                0,
                10,
                List.of("official")
        ));
        assertThat(anonymousResult.skillIds()).isEmpty();

        SearchResult memberResult = queryService.search(new SearchQuery(
                "agent",
                20L,
                new SearchVisibilityScope("user-1", Set.of(20L), Set.of(), false),
                "relevance",
                0,
                10,
                List.of("official")
        ));

        assertThat(memberResult.skillIds()).containsExactly(202L);
        assertThat(memberResult.total()).isEqualTo(1L);
    }

    @Test
    void search_shouldSupportSortingModesAndPagination() {
        LocalFileIndexService indexService = new LocalFileIndexService(tempDir);
        LocalFileIndexQueryService queryService = new LocalFileIndexQueryService(tempDir);
        indexService.batchIndex(List.of(
                document(1L, 10L, "global", "PUBLIC", "ACTIVE", "ACTIVE", false,
                        "Alpha", "First", "tools", "alpha workflows",
                        List.of("a"), 15L, 4.0D, 100L),
                document(2L, 10L, "global", "PUBLIC", "ACTIVE", "ACTIVE", false,
                        "Beta", "Second", "tools", "beta workflows",
                        List.of("a"), 30L, 3.8D, 200L),
                document(3L, 10L, "global", "PUBLIC", "ACTIVE", "ACTIVE", false,
                        "Gamma", "Third", "tools", "gamma workflows",
                        List.of("a"), 20L, 4.9D, 150L)
        ));

        assertThat(queryService.search(new SearchQuery(
                " ",
                null,
                SearchVisibilityScope.anonymous(),
                "downloads",
                0,
                3,
                List.of()
        )).skillIds()).containsExactly(2L, 3L, 1L);

        assertThat(queryService.search(new SearchQuery(
                " ",
                null,
                SearchVisibilityScope.anonymous(),
                "rating",
                0,
                3,
                List.of()
        )).skillIds()).containsExactly(3L, 1L, 2L);

        SearchResult newestPage = queryService.search(new SearchQuery(
                " ",
                null,
                SearchVisibilityScope.anonymous(),
                "newest",
                1,
                2,
                List.of()
        ));

        assertThat(newestPage.skillIds()).containsExactly(1L);
        assertThat(newestPage.total()).isEqualTo(3L);
        assertThat(newestPage.page()).isEqualTo(1);
        assertThat(newestPage.size()).isEqualTo(2);
    }

    @Test
    void search_shouldFallBackToNewestForWhitespaceRelevanceAndUnknownSort() {
        LocalFileIndexService indexService = new LocalFileIndexService(tempDir);
        LocalFileIndexQueryService queryService = new LocalFileIndexQueryService(tempDir);
        indexService.batchIndex(List.of(
                document(1L, 10L, "global", "PUBLIC", "ACTIVE", "ACTIVE", false,
                        "Older", "Older", "tools", "older",
                        List.of(), 1L, 1.0D, 100L),
                document(2L, 10L, "global", "PUBLIC", "ACTIVE", "ACTIVE", false,
                        "Newest", "Newest", "tools", "newest",
                        List.of(), 1L, 1.0D, 200L)
        ));

        assertThat(queryService.search(new SearchQuery(
                "   ",
                null,
                SearchVisibilityScope.anonymous(),
                "relevance",
                0,
                10,
                List.of()
        )).skillIds()).containsExactly(2L, 1L);

        assertThat(queryService.search(new SearchQuery(
                null,
                null,
                SearchVisibilityScope.anonymous(),
                "drop table",
                0,
                10,
                List.of()
        )).skillIds()).containsExactly(2L, 1L);
    }

    @Test
    void search_shouldReturnEmptyResultWhenIndexDirectoryHasNotBeenBuiltYet() {
        LocalFileIndexQueryService queryService = new LocalFileIndexQueryService(tempDir.resolve("missing-index"));

        SearchResult result = queryService.search(new SearchQuery(
                "agent",
                null,
                SearchVisibilityScope.anonymous(),
                "relevance",
                0,
                12,
                List.of()
        ));

        assertThat(result.skillIds()).isEmpty();
        assertThat(result.total()).isZero();
    }

    @Test
    void search_shouldReturnEmptyResultForFreshButUnindexedDirectory() throws Exception {
        Path freshDir = tempDir.resolve("fresh-index");
        Files.createDirectories(freshDir);
        LocalFileIndexQueryService queryService = new LocalFileIndexQueryService(freshDir);

        SearchResult result = queryService.search(new SearchQuery(
                "agent",
                null,
                SearchVisibilityScope.anonymous(),
                "relevance",
                0,
                12,
                List.of()
        ));

        assertThat(result.skillIds()).isEmpty();
        assertThat(result.total()).isZero();
    }

    @Test
    void search_shouldReturnTotalButNoHitsWhenOffsetExceedsResultSetOrPageSizeZero() {
        LocalFileIndexService indexService = new LocalFileIndexService(tempDir);
        LocalFileIndexQueryService queryService = new LocalFileIndexQueryService(tempDir);
        indexService.batchIndex(List.of(
                document(1L, 10L, "global", "PUBLIC", "ACTIVE", "ACTIVE", false,
                        "Alpha", "First", "tools", "alpha workflows", List.of("a"), 1L, 1.0D, 100L),
                document(2L, 10L, "global", "PUBLIC", "ACTIVE", "ACTIVE", false,
                        "Beta", "Second", "tools", "beta workflows", List.of("a"), 2L, 2.0D, 200L)
        ));

        SearchResult overflowPage = queryService.search(new SearchQuery(
                null,
                null,
                SearchVisibilityScope.anonymous(),
                "newest",
                3,
                2,
                List.of()
        ));
        SearchResult zeroSize = queryService.search(new SearchQuery(
                null,
                null,
                SearchVisibilityScope.anonymous(),
                "newest",
                0,
                0,
                List.of()
        ));

        assertThat(overflowPage.skillIds()).isEmpty();
        assertThat(overflowPage.total()).isEqualTo(2L);
        assertThat(zeroSize.skillIds()).isEmpty();
        assertThat(zeroSize.total()).isEqualTo(2L);
    }

    @Test
    void search_shouldHideNamespaceOnlyResultsWhenAuthenticatedUserHasNoMemberNamespaces() {
        LocalFileIndexService indexService = new LocalFileIndexService(tempDir);
        LocalFileIndexQueryService queryService = new LocalFileIndexQueryService(tempDir);
        indexService.index(document(1L, 20L, "team-ai", "NAMESPACE_ONLY", "ACTIVE", "ACTIVE", false,
                "Team Agent", "Member only", "agent", "team search", List.of("official"), 1L, 1.0D, 100L));

        SearchResult result = queryService.search(new SearchQuery(
                "agent",
                null,
                new SearchVisibilityScope("user-1", Set.of(), Set.of(), false),
                "relevance",
                0,
                10,
                List.of()
        ));

        assertThat(result.skillIds()).isEmpty();
        assertThat(result.total()).isZero();
    }

    @Test
    void search_shouldIgnoreBlankLabelFiltersAndDefaultNullSortToNewest() {
        LocalFileIndexService indexService = new LocalFileIndexService(tempDir);
        LocalFileIndexQueryService queryService = new LocalFileIndexQueryService(tempDir);
        indexService.batchIndex(List.of(
                document(1L, 10L, "global", "PUBLIC", "ACTIVE", "ACTIVE", false,
                        "Older", "Older", "tools", "older", List.of("official"), 1L, 1.0D, 100L),
                document(2L, 10L, "global", "PUBLIC", "ACTIVE", "ACTIVE", false,
                        "Newer", "Newer", "tools", "newer", List.of("beta"), 1L, 1.0D, 200L)
        ));

        SearchResult result = queryService.search(new SearchQuery(
                null,
                null,
                SearchVisibilityScope.anonymous(),
                null,
                0,
                10,
                Arrays.asList(" ", null)
        ));

        assertThat(result.skillIds()).containsExactly(2L, 1L);
        assertThat(result.total()).isEqualTo(2L);
    }

    @Test
    void search_shouldWrapDirectoryIoFailures() {
        LocalFileIndexQueryService failingService = new LocalFileIndexQueryService(tempDir) {
            @Override
            protected org.apache.lucene.store.Directory openDirectory(Path directory) throws java.io.IOException {
                throw new java.io.IOException("boom");
            }
        };

        assertThatThrownBy(() -> failingService.search(new SearchQuery(
                "agent",
                null,
                SearchVisibilityScope.anonymous(),
                "relevance",
                0,
                10,
                List.of()
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to query local file index");
    }

    @Test
    void search_shouldWrapAnalyzerFailures() {
        LocalFileIndexService indexService = new LocalFileIndexService(tempDir);
        indexService.index(document(1L, 10L, "global", "PUBLIC", "ACTIVE", "ACTIVE", false,
                "Agent", "Summary", "keywords", "search", List.of(), 1L, 1.0D, 1L));

        LocalFileIndexQueryService failingService = new LocalFileIndexQueryService(tempDir) {
            @Override
            protected List<String> analyze(String keyword) throws java.io.IOException {
                throw new java.io.IOException("tokenizer down");
            }
        };

        assertThatThrownBy(() -> failingService.search(new SearchQuery(
                "agent",
                null,
                SearchVisibilityScope.anonymous(),
                "relevance",
                0,
                10,
                List.of()
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to tokenize local file index keyword query");
    }

    @Test
    void search_shouldTreatAnalyzerWithoutTermsAsMatchAll() {
        LocalFileIndexService indexService = new LocalFileIndexService(tempDir);
        indexService.batchIndex(List.of(
                document(1L, 10L, "global", "PUBLIC", "ACTIVE", "ACTIVE", false,
                        "Alpha", "First", "tools", "alpha workflows", List.of("a"), 1L, 1.0D, 100L),
                document(2L, 10L, "global", "PUBLIC", "ACTIVE", "ACTIVE", false,
                        "Beta", "Second", "tools", "beta workflows", List.of("b"), 2L, 2.0D, 200L)
        ));

        LocalFileIndexQueryService queryService = new LocalFileIndexQueryService(tempDir) {
            @Override
            protected List<String> analyze(String keyword) {
                return List.of();
            }
        };

        SearchResult result = queryService.search(new SearchQuery(
                "agent",
                null,
                SearchVisibilityScope.anonymous(),
                "newest",
                0,
                10,
                List.of()
        ));

        assertThat(result.skillIds()).containsExactly(2L, 1L);
        assertThat(result.total()).isEqualTo(2L);
    }

    private SkillSearchDocument document(
            Long skillId,
            Long namespaceId,
            String namespaceSlug,
            String visibility,
            String status,
            String namespaceStatus,
            boolean hidden,
            String title,
            String summary,
            String keywords,
            String searchText,
            List<String> labelSlugs,
            long downloadCount,
            double ratingAvg,
            long updatedAtEpochMillis) {
        return new SkillSearchDocument(
                skillId,
                namespaceId,
                namespaceSlug,
                "owner-" + skillId,
                title,
                summary,
                keywords,
                searchText,
                null,
                visibility,
                status,
                labelSlugs,
                downloadCount,
                ratingAvg,
                updatedAtEpochMillis,
                namespaceStatus,
                hidden
        );
    }
}
