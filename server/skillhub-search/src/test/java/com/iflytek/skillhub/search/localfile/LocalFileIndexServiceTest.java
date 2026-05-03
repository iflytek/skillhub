package com.iflytek.skillhub.search.localfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.search.SkillSearchDocument;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileIndexServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void indexShouldUpsertAndRemoveBySkillId() throws Exception {
        LocalFileIndexService service = new LocalFileIndexService(tempDir);

        service.index(document(1L, "First title", "alpha beta"));
        service.index(document(1L, "Updated title", "gamma delta"));

        assertThat(hitCount("1")).isEqualTo(1);
        assertThat(storedField("1", "title")).isEqualTo("Updated title");

        service.remove(1L);

        assertThat(docCount()).isZero();
    }

    @Test
    void batchIndexShouldPersistEachDocument() throws Exception {
        LocalFileIndexService service = new LocalFileIndexService(tempDir);

        service.batchIndex(List.of(
                document(1L, "First title", "alpha beta"),
                document(2L, "Second title", "gamma delta")
        ));

        assertThat(docCount()).isEqualTo(2);
        assertThat(hitCount("1")).isEqualTo(1);
        assertThat(hitCount("2")).isEqualTo(1);
    }

    @Test
    void batchIndexAndRemoveShouldIgnoreEmptyInputsAndNormalizeLabels() throws Exception {
        LocalFileIndexService service = new LocalFileIndexService(tempDir);

        service.batchIndex(null);
        service.batchIndex(List.of());
        service.remove(null);
        service.index(new SkillSearchDocument(
                9L,
                10L,
                null,
                null,
                "Title",
                null,
                null,
                null,
                null,
                "PUBLIC",
                "ACTIVE",
                Arrays.asList(" Official ", null, " ", "beta"),
                0L,
                0D,
                0L,
                "ACTIVE",
                false
        ));

        assertThat(docCount()).isEqualTo(1);
        assertThat(storedField("9", "namespaceSlug")).isNull();
        assertThat(storedField("9", "ownerId")).isNull();
        assertThat(hitCountByLabel("official")).isEqualTo(1);
        assertThat(hitCountByLabel("beta")).isEqualTo(1);
    }

    @Test
    void indexShouldRejectNullDocument() {
        LocalFileIndexService service = new LocalFileIndexService(tempDir);

        assertThatThrownBy(() -> service.index(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("document");
    }

    @Test
    void indexShouldWrapIndexWriterIoFailures() {
        LocalFileIndexService service = new LocalFileIndexService(tempDir) {
            @Override
            protected void ensureIndexDirectory(Path directory) {
            }

            @Override
            protected Directory openDirectory(Path directory) throws java.io.IOException {
                throw new java.io.IOException("boom");
            }
        };

        assertThatThrownBy(() -> service.index(document(1L, "Title", "keywords")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to update local file index");
    }

    private SkillSearchDocument document(Long skillId, String title, String keywords) {
        return new SkillSearchDocument(
                skillId,
                10L,
                "team-ai",
                "owner-1",
                title,
                "Builds workflows",
                keywords,
                "search text",
                null,
                "PUBLIC",
                "ACTIVE",
                List.of("official"),
                3L,
                4.5D,
                123L,
                "ACTIVE",
                false
        );
    }

    private long hitCount(String skillId) throws Exception {
        try (Directory directory = FSDirectory.open(tempDir);
             IndexReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            return searcher.search(new TermQuery(new Term("skillId", skillId)), 10).totalHits.value;
        }
    }

    private String storedField(String skillId, String fieldName) throws Exception {
        try (Directory directory = FSDirectory.open(tempDir);
             IndexReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            int docId = searcher.search(new TermQuery(new Term("skillId", skillId)), 10).scoreDocs[0].doc;
            Document document = reader.storedFields().document(docId);
            return document.get(fieldName);
        }
    }

    private long docCount() throws Exception {
        try (Directory directory = FSDirectory.open(tempDir);
             IndexReader reader = DirectoryReader.open(directory)) {
            return reader.numDocs();
        }
    }

    private long hitCountByLabel(String labelSlug) throws Exception {
        try (Directory directory = FSDirectory.open(tempDir);
             IndexReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            return searcher.search(new TermQuery(new Term("labelSlug", labelSlug)), 10).totalHits.value;
        }
    }
}
