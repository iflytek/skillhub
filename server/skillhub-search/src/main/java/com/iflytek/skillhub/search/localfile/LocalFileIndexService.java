package com.iflytek.skillhub.search.localfile;

import com.iflytek.skillhub.search.SearchIndexService;
import com.iflytek.skillhub.search.SkillSearchDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lucene-backed local file index writer for the phase-three search backend.
 */
@Service
@ConditionalOnProperty(prefix = "skillhub.search", name = "provider", havingValue = "local-file-index")
public class LocalFileIndexService implements SearchIndexService {
    private final Path indexDirectory;

    public LocalFileIndexService(
            @Value("${skillhub.search.local-file-index.directory}") Path indexDirectory) {
        this.indexDirectory = indexDirectory;
    }

    @Override
    @Transactional
    public void index(SkillSearchDocument document) {
        writeDocuments(List.of(Objects.requireNonNull(document, "document")));
    }

    @Override
    @Transactional
    public void batchIndex(List<SkillSearchDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        writeDocuments(documents);
    }

    @Override
    @Transactional
    public void remove(Long skillId) {
        if (skillId == null) {
            return;
        }
        withIndexWriter(writer -> writer.deleteDocuments(new Term(FIELD_SKILL_ID, String.valueOf(skillId))));
    }

    private void writeDocuments(List<SkillSearchDocument> documents) {
        withIndexWriter(writer -> {
            for (SkillSearchDocument document : documents) {
                SkillSearchDocument nonNullDocument = Objects.requireNonNull(document, "document");
                writer.updateDocument(
                        new Term(FIELD_SKILL_ID, String.valueOf(nonNullDocument.skillId())),
                        toLuceneDocument(nonNullDocument)
                );
            }
        });
    }

    private void withIndexWriter(IndexWriterAction action) {
        try {
            ensureIndexDirectory(indexDirectory);
            try (Directory directory = openDirectory(indexDirectory);
                 IndexWriter writer = new IndexWriter(directory, newIndexWriterConfig())) {
                action.accept(writer);
                writer.commit();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to update local file index at " + indexDirectory, e);
        }
    }

    protected void ensureIndexDirectory(Path directory) throws IOException {
        Files.createDirectories(directory);
    }

    protected Directory openDirectory(Path directory) throws IOException {
        return FSDirectory.open(directory);
    }

    private IndexWriterConfig newIndexWriterConfig() {
        return new IndexWriterConfig(new StandardAnalyzer())
                .setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
    }

    private Document toLuceneDocument(SkillSearchDocument document) {
        Document luceneDocument = new Document();
        luceneDocument.add(new StringField(FIELD_SKILL_ID, String.valueOf(document.skillId()), Field.Store.YES));
        addSortableLongField(luceneDocument, FIELD_SKILL_ID_SORT, document.skillId());
        luceneDocument.add(new LongPoint(FIELD_NAMESPACE_ID, document.namespaceId()));
        luceneDocument.add(new StoredField(FIELD_NAMESPACE_ID, document.namespaceId()));
        addStoredStringField(luceneDocument, FIELD_NAMESPACE_SLUG, document.namespaceSlug());
        addStoredStringField(luceneDocument, FIELD_OWNER_ID, document.ownerId());
        addStoredTextField(luceneDocument, FIELD_TITLE, document.title());
        addStoredTextField(luceneDocument, FIELD_SUMMARY, document.summary());
        addStoredTextField(luceneDocument, FIELD_KEYWORDS, document.keywords());
        addStoredTextField(luceneDocument, FIELD_SEARCH_TEXT, document.searchText());
        addStoredStringField(luceneDocument, FIELD_SEMANTIC_VECTOR, document.semanticVector());
        addStoredStringField(luceneDocument, FIELD_VISIBILITY, document.visibility());
        addStoredStringField(luceneDocument, FIELD_STATUS, document.status());
        addStoredStringField(luceneDocument, FIELD_NAMESPACE_STATUS, document.namespaceStatus());
        addStoredBooleanField(luceneDocument, FIELD_HIDDEN, document.hidden());
        addStoredLongField(luceneDocument, FIELD_DOWNLOAD_COUNT, document.downloadCount());
        addStoredDoubleField(luceneDocument, FIELD_RATING_AVG, document.ratingAvg());
        addStoredLongField(luceneDocument, FIELD_UPDATED_AT_EPOCH_MILLIS, document.updatedAtEpochMillis());
        addSortableLongField(luceneDocument, FIELD_DOWNLOAD_COUNT_SORT, document.downloadCount());
        addSortableDoubleField(luceneDocument, FIELD_RATING_AVG_SORT, document.ratingAvg());
        addSortableLongField(luceneDocument, FIELD_UPDATED_AT_EPOCH_MILLIS_SORT, document.updatedAtEpochMillis());
        addLabelFields(luceneDocument, document.labelSlugs());
        return luceneDocument;
    }

    private void addStoredStringField(Document document, String fieldName, String value) {
        if (value != null) {
            document.add(new StringField(fieldName, value, Field.Store.YES));
        }
    }

    private void addStoredBooleanField(Document document, String fieldName, boolean value) {
        document.add(new StringField(fieldName, Boolean.toString(value), Field.Store.YES));
    }

    private void addStoredLongField(Document document, String fieldName, long value) {
        document.add(new LongPoint(fieldName, value));
        document.add(new StoredField(fieldName, value));
    }

    private void addStoredDoubleField(Document document, String fieldName, double value) {
        document.add(new DoublePoint(fieldName, value));
        document.add(new StoredField(fieldName, value));
    }

    private void addSortableLongField(Document document, String fieldName, long value) {
        document.add(new NumericDocValuesField(fieldName, value));
        document.add(new StoredField(fieldName, value));
    }

    private void addSortableDoubleField(Document document, String fieldName, double value) {
        document.add(new DoubleDocValuesField(fieldName, value));
        document.add(new StoredField(fieldName, value));
    }

    private void addStoredTextField(Document document, String fieldName, String value) {
        if (value != null) {
            document.add(new TextField(fieldName, value, Field.Store.YES));
        }
    }

    private void addLabelFields(Document document, List<String> labelSlugs) {
        if (labelSlugs == null || labelSlugs.isEmpty()) {
            return;
        }
        for (String labelSlug : labelSlugs) {
            if (labelSlug == null) {
                continue;
            }
            String normalized = labelSlug.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isBlank()) {
                document.add(new StringField(FIELD_LABEL_SLUG, normalized, Field.Store.YES));
            }
        }
    }

    @FunctionalInterface
    private interface IndexWriterAction {
        void accept(IndexWriter writer) throws IOException;
    }

    static final String FIELD_SKILL_ID = "skillId";
    static final String FIELD_NAMESPACE_ID = "namespaceId";
    static final String FIELD_SKILL_ID_SORT = "skillIdSort";
    static final String FIELD_NAMESPACE_SLUG = "namespaceSlug";
    static final String FIELD_OWNER_ID = "ownerId";
    static final String FIELD_TITLE = "title";
    static final String FIELD_SUMMARY = "summary";
    static final String FIELD_KEYWORDS = "keywords";
    static final String FIELD_SEARCH_TEXT = "searchText";
    static final String FIELD_SEMANTIC_VECTOR = "semanticVector";
    static final String FIELD_VISIBILITY = "visibility";
    static final String FIELD_STATUS = "status";
    static final String FIELD_LABEL_SLUG = "labelSlug";
    static final String FIELD_DOWNLOAD_COUNT = "downloadCount";
    static final String FIELD_DOWNLOAD_COUNT_SORT = "downloadCountSort";
    static final String FIELD_RATING_AVG = "ratingAvg";
    static final String FIELD_RATING_AVG_SORT = "ratingAvgSort";
    static final String FIELD_UPDATED_AT_EPOCH_MILLIS = "updatedAtEpochMillis";
    static final String FIELD_UPDATED_AT_EPOCH_MILLIS_SORT = "updatedAtEpochMillisSort";
    static final String FIELD_NAMESPACE_STATUS = "namespaceStatus";
    static final String FIELD_HIDDEN = "hidden";
}
