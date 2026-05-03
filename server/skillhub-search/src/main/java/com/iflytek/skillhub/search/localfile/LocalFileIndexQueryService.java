package com.iflytek.skillhub.search.localfile;

import com.iflytek.skillhub.search.SearchQuery;
import com.iflytek.skillhub.search.SearchQueryService;
import com.iflytek.skillhub.search.SearchResult;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortedNumericSortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Lucene-backed read path for the local-file-index provider.
 */
@Service
@ConditionalOnProperty(prefix = "skillhub.search", name = "provider", havingValue = "local-file-index")
public class LocalFileIndexQueryService implements SearchQueryService {
    private static final String[] KEYWORD_FIELDS = {
            LocalFileIndexService.FIELD_TITLE,
            LocalFileIndexService.FIELD_SUMMARY,
            LocalFileIndexService.FIELD_KEYWORDS,
            LocalFileIndexService.FIELD_SEARCH_TEXT
    };

    private final Path indexDirectory;
    private final StandardAnalyzer analyzer = new StandardAnalyzer();

    public LocalFileIndexQueryService(
            @Value("${skillhub.search.local-file-index.directory}") Path indexDirectory) {
        this.indexDirectory = indexDirectory;
    }

    @Override
    public SearchResult search(SearchQuery query) {
        if (Files.notExists(indexDirectory)) {
            return emptyResult(query);
        }

        try (Directory directory = FSDirectory.open(indexDirectory)) {
            if (!DirectoryReader.indexExists(directory)) {
                return emptyResult(query);
            }
            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                Query luceneQuery = buildQuery(query);
                int total = searcher.count(luceneQuery);
                if (total == 0) {
                    return emptyResult(query);
                }

                int offset = Math.max(0, query.page() * query.size());
                int limit = Math.max(0, query.size());
                if (limit == 0 || offset >= total) {
                    return new SearchResult(List.of(), total, query.page(), query.size());
                }

                List<Long> skillIds = collectPageSkillIds(searcher, reader, luceneQuery, query, offset, limit, total);
                return new SearchResult(Collections.unmodifiableList(skillIds), total, query.page(), query.size());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to query local file index at " + indexDirectory, e);
        }
    }

    private List<Long> collectPageSkillIds(
            IndexSearcher searcher,
            DirectoryReader reader,
            Query luceneQuery,
            SearchQuery query,
            int offset,
            int limit,
            int total) throws IOException {
        int requestedHits = Math.min(total, offset + limit);
        TopDocs topDocs = searcher.searchAfter(null, luceneQuery, requestedHits, buildSort(query));
        List<Long> skillIds = new ArrayList<>(Math.min(limit, Math.max(0, topDocs.scoreDocs.length - offset)));
        for (int i = offset; i < topDocs.scoreDocs.length; i++) {
            Document document = reader.storedFields().document(topDocs.scoreDocs[i].doc);
            String skillId = document.get(LocalFileIndexService.FIELD_SKILL_ID);
            if (skillId != null) {
                skillIds.add(Long.valueOf(skillId));
            }
        }
        return skillIds;
    }

    private Query buildQuery(SearchQuery query) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(buildKeywordQuery(query.keyword()), Occur.MUST);
        builder.add(buildVisibilityQuery(query), Occur.MUST);
        builder.add(new TermQuery(new Term(LocalFileIndexService.FIELD_STATUS, "ACTIVE")), Occur.MUST);
        builder.add(new TermQuery(new Term(LocalFileIndexService.FIELD_NAMESPACE_STATUS, "ACTIVE")), Occur.MUST);
        builder.add(new TermQuery(new Term(LocalFileIndexService.FIELD_HIDDEN, Boolean.FALSE.toString())), Occur.MUST);

        if (query.namespaceId() != null) {
            builder.add(LongPoint.newExactQuery(
                    LocalFileIndexService.FIELD_NAMESPACE_ID,
                    query.namespaceId()
            ), Occur.MUST);
        }

        if (query.labelSlugs() != null && !query.labelSlugs().isEmpty()) {
            for (String labelSlug : query.labelSlugs()) {
                String normalized = normalizeLabelSlug(labelSlug);
                if (normalized != null) {
                    builder.add(new TermQuery(new Term(LocalFileIndexService.FIELD_LABEL_SLUG, normalized)), Occur.MUST);
                }
            }
        }

        return builder.build();
    }

    private Query buildVisibilityQuery(SearchQuery query) {
        if (query.visibilityScope().userId() == null) {
            return new TermQuery(new Term(LocalFileIndexService.FIELD_VISIBILITY, "PUBLIC"));
        }
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(new TermQuery(new Term(LocalFileIndexService.FIELD_VISIBILITY, "PUBLIC")), Occur.SHOULD);
        BooleanQuery.Builder namespaceOnly = new BooleanQuery.Builder();
        namespaceOnly.add(new TermQuery(new Term(LocalFileIndexService.FIELD_VISIBILITY, "NAMESPACE_ONLY")), Occur.MUST);
        namespaceOnly.add(buildMemberNamespaceQuery(query.visibilityScope().memberNamespaceIds()), Occur.MUST);
        builder.add(namespaceOnly.build(), Occur.SHOULD);
        return builder.build();
    }

    private Query buildMemberNamespaceQuery(Set<Long> memberNamespaceIds) {
        if (memberNamespaceIds == null || memberNamespaceIds.isEmpty()) {
            return LongPoint.newExactQuery(LocalFileIndexService.FIELD_NAMESPACE_ID, -1L);
        }
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (Long namespaceId : memberNamespaceIds) {
            if (namespaceId != null) {
                builder.add(LongPoint.newExactQuery(LocalFileIndexService.FIELD_NAMESPACE_ID, namespaceId), Occur.SHOULD);
            }
        }
        return builder.build();
    }

    private Query buildKeywordQuery(String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        if (normalizedKeyword == null) {
            return new MatchAllDocsQuery();
        }
        try {
            List<String> terms = analyze(normalizedKeyword);
            if (terms.isEmpty()) {
                return new MatchAllDocsQuery();
            }

            BooleanQuery.Builder outer = new BooleanQuery.Builder();
            for (String term : terms) {
                outer.add(buildPerTermFieldQuery(term), Occur.MUST);
            }
            return outer.build();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to tokenize local file index keyword query", e);
        }
    }

    private Query buildPerTermFieldQuery(String term) {
        BooleanQuery.Builder fieldMatches = new BooleanQuery.Builder();
        fieldMatches.add(boostedTermQuery(LocalFileIndexService.FIELD_TITLE, term, 2f), Occur.SHOULD);
        fieldMatches.add(boostedPrefixQuery(LocalFileIndexService.FIELD_TITLE, term, 1.5f), Occur.SHOULD);
        for (String field : KEYWORD_FIELDS) {
            fieldMatches.add(new TermQuery(new Term(field, term)), Occur.SHOULD);
        }
        return fieldMatches.build();
    }

    private Query boostedTermQuery(String field, String term, float boost) {
        return new BoostQuery(new TermQuery(new Term(field, term)), boost);
    }

    private Query boostedPrefixQuery(String field, String term, float boost) {
        return new BoostQuery(new ConstantScoreQuery(new org.apache.lucene.search.PrefixQuery(new Term(field, term))), boost);
    }

    private Sort buildSort(SearchQuery query) {
        String sortBy = normalizeSortBy(query.sortBy());
        boolean hasKeyword = normalizeKeyword(query.keyword()) != null;
        if ("downloads".equals(sortBy)) {
            return new Sort(
                    new SortedNumericSortField(LocalFileIndexService.FIELD_DOWNLOAD_COUNT_SORT, SortField.Type.LONG, true),
                    new SortedNumericSortField(LocalFileIndexService.FIELD_UPDATED_AT_EPOCH_MILLIS_SORT, SortField.Type.LONG, true),
                    new SortedNumericSortField(LocalFileIndexService.FIELD_SKILL_ID_SORT, SortField.Type.LONG, true)
            );
        }
        if ("rating".equals(sortBy)) {
            return new Sort(
                    new SortedNumericSortField(LocalFileIndexService.FIELD_RATING_AVG_SORT, SortField.Type.DOUBLE, true),
                    new SortedNumericSortField(LocalFileIndexService.FIELD_UPDATED_AT_EPOCH_MILLIS_SORT, SortField.Type.LONG, true),
                    new SortedNumericSortField(LocalFileIndexService.FIELD_SKILL_ID_SORT, SortField.Type.LONG, true)
            );
        }
        if ("newest".equals(sortBy) || ("relevance".equals(sortBy) && !hasKeyword)) {
            return new Sort(
                    new SortedNumericSortField(LocalFileIndexService.FIELD_UPDATED_AT_EPOCH_MILLIS_SORT, SortField.Type.LONG, true),
                    new SortedNumericSortField(LocalFileIndexService.FIELD_SKILL_ID_SORT, SortField.Type.LONG, true)
            );
        }
        return Sort.RELEVANCE;
    }

    private String normalizeSortBy(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "newest";
        }
        String normalized = sortBy.trim().toLowerCase(Locale.ROOT);
        if (List.of("relevance", "downloads", "rating", "newest").contains(normalized)) {
            return normalized;
        }
        return "newest";
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeLabelSlug(String labelSlug) {
        if (labelSlug == null || labelSlug.isBlank()) {
            return null;
        }
        return labelSlug.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> analyze(String keyword) throws IOException {
        List<String> terms = new ArrayList<>();
        try (var tokenStream = analyzer.tokenStream(LocalFileIndexService.FIELD_SEARCH_TEXT, new StringReader(keyword))) {
            CharTermAttribute termAttribute = tokenStream.addAttribute(CharTermAttribute.class);
            tokenStream.reset();
            while (tokenStream.incrementToken()) {
                terms.add(termAttribute.toString());
            }
            tokenStream.end();
        }
        return terms;
    }

    private SearchResult emptyResult(SearchQuery query) {
        return new SearchResult(List.of(), 0, query.page(), query.size());
    }
}
