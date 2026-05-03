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
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
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
                Query luceneQuery = buildQuery(query.keyword());
                int total = searcher.count(luceneQuery);
                if (total == 0) {
                    return emptyResult(query);
                }

                int offset = Math.max(0, query.page() * query.size());
                int limit = Math.max(0, query.size());
                int requestedHits = Math.min(total, offset + limit);
                if (limit == 0 || offset >= total) {
                    return new SearchResult(List.of(), total, query.page(), query.size());
                }

                ScoreDoc[] scoreDocs = searcher.search(luceneQuery, requestedHits).scoreDocs;
                List<Long> skillIds = new java.util.ArrayList<>(Math.min(limit, Math.max(0, scoreDocs.length - offset)));
                for (int i = offset; i < scoreDocs.length; i++) {
                    String skillId = reader.storedFields()
                            .document(scoreDocs[i].doc)
                            .get(LocalFileIndexService.FIELD_SKILL_ID);
                    if (skillId != null) {
                        skillIds.add(Long.valueOf(skillId));
                    }
                }
                return new SearchResult(Collections.unmodifiableList(skillIds), total, query.page(), query.size());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to query local file index at " + indexDirectory, e);
        }
    }

    private Query buildQuery(String keyword) {
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
                BooleanQuery.Builder fieldMatches = new BooleanQuery.Builder();
                for (String field : KEYWORD_FIELDS) {
                    fieldMatches.add(new TermQuery(new Term(field, term)), BooleanClause.Occur.SHOULD);
                }
                outer.add(fieldMatches.build(), BooleanClause.Occur.MUST);
            }
            return outer.build();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to tokenize local file index keyword query", e);
        }
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
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
