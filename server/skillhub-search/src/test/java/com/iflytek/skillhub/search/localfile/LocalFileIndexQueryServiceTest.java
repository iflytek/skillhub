package com.iflytek.skillhub.search.localfile;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.search.SearchQuery;
import com.iflytek.skillhub.search.SearchResult;
import com.iflytek.skillhub.search.SearchVisibilityScope;
import com.iflytek.skillhub.search.SkillSearchDocument;
import java.nio.file.Path;
import java.util.List;
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
                document(11L, "Prompt Agent", "Builds workflows", "automation", "multi step pipelines"),
                document(22L, "Search Helper", "Finds indexed content", "discovery", "lucene agent gateway"),
                document(33L, "Billing Tool", "Finance utility", "ledger", "invoice export")
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
    void search_shouldTreatWhitespaceKeywordAsMatchAllAndHonorPaginationMetadata() {
        LocalFileIndexService indexService = new LocalFileIndexService(tempDir);
        LocalFileIndexQueryService queryService = new LocalFileIndexQueryService(tempDir);
        indexService.batchIndex(List.of(
                document(101L, "Alpha", "First", "one", "first doc"),
                document(202L, "Beta", "Second", "two", "second doc"),
                document(303L, "Gamma", "Third", "three", "third doc")
        ));

        SearchResult result = queryService.search(new SearchQuery(
                "   ",
                null,
                SearchVisibilityScope.anonymous(),
                "newest",
                1,
                2,
                List.of()
        ));

        assertThat(result.skillIds()).hasSize(1);
        assertThat(result.total()).isEqualTo(3L);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(2);
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

    private SkillSearchDocument document(
            Long skillId,
            String title,
            String summary,
            String keywords,
            String searchText) {
        return new SkillSearchDocument(
                skillId,
                10L,
                "global",
                "owner-1",
                title,
                summary,
                keywords,
                searchText,
                null,
                "PUBLIC",
                "ACTIVE"
        );
    }
}
