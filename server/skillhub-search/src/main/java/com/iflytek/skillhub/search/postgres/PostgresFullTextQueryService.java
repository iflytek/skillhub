package com.iflytek.skillhub.search.postgres;

import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentJpaRepository;
import com.iflytek.skillhub.search.SearchEmbeddingService;
import com.iflytek.skillhub.search.SearchQuery;
import com.iflytek.skillhub.search.SearchQueryService;
import com.iflytek.skillhub.search.SearchResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.Comparator;
import java.util.HashMap;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * PostgreSQL-backed implementation of {@link SearchQueryService}.
 *
 * <p>The query pipeline combines structured visibility filters, full-text
 * ranking, and an optional semantic re-ranking pass over a bounded candidate
 * set.
 */
@Service
public class PostgresFullTextQueryService implements SearchQueryService {
    private static final Pattern QUERY_TERM_SPLITTER = Pattern.compile("[^\\p{L}\\p{N}_]+");
    private static final int MAX_QUERY_TERMS = 8;
    private static final int SHORT_PREFIX_LENGTH = 2;
    private static final String TITLE_VECTOR_SQL = "to_tsvector('simple', coalesce(title, ''))";
    private static final String TITLE_SQL = "LOWER(title)";

    private final EntityManager entityManager;
    private final SkillSearchDocumentJpaRepository searchDocumentRepository;
    private final SearchEmbeddingService searchEmbeddingService;
    private final boolean semanticEnabled;
    private final double semanticWeight;
    private final int candidateMultiplier;
    private final int maxCandidates;

    public PostgresFullTextQueryService(EntityManager entityManager) {
        this(entityManager, null, null, false, 0.35D, 8, 120);
    }

    @Autowired
    public PostgresFullTextQueryService(EntityManager entityManager,
                                        SkillSearchDocumentJpaRepository searchDocumentRepository,
                                        SearchEmbeddingService searchEmbeddingService,
                                        @Value("${skillhub.search.semantic.enabled:true}") boolean semanticEnabled,
                                        @Value("${skillhub.search.semantic.weight:0.35}") double semanticWeight,
                                        @Value("${skillhub.search.semantic.candidate-multiplier:8}") int candidateMultiplier,
                                        @Value("${skillhub.search.semantic.max-candidates:120}") int maxCandidates) {
        this.entityManager = entityManager;
        this.searchDocumentRepository = searchDocumentRepository;
        this.searchEmbeddingService = searchEmbeddingService;
        this.semanticEnabled = semanticEnabled;
        this.semanticWeight = semanticWeight;
        this.candidateMultiplier = candidateMultiplier;
        this.maxCandidates = maxCandidates;
    }

    /**
     * Executes a search query against the denormalized search document table
     * and optionally re-ranks candidates using embeddings.
     */
    @Override
    public SearchResult search(SearchQuery query) {
        String normalizedKeyword = normalizeKeyword(query.keyword());
        String tsQuery = buildPrefixTsQuery(normalizedKeyword);
        boolean hasKeyword = normalizedKeyword != null;
        boolean hasTsQuery = tsQuery != null;
        boolean useRelevanceOrdering = "relevance".equals(query.sortBy()) && hasKeyword;
        boolean useShortPrefixTitleSearch = hasTsQuery && normalizedKeyword.length() <= SHORT_PREFIX_LENGTH;
        boolean useSemanticRerank = semanticEnabled
                && hasKeyword
                && "relevance".equals(query.sortBy())
                && searchDocumentRepository != null
                && searchEmbeddingService != null;
        int requestedOffset = query.page() * query.size();
        if (useSemanticRerank && requestedOffset + query.size() > maxCandidates) {
            useSemanticRerank = false;
        }
        int sqlLimit = query.size();
        int sqlOffset = requestedOffset;
        if (useSemanticRerank) {
            sqlLimit = Math.min(Math.max((query.page() + 1) * query.size() * candidateMultiplier, query.size() * candidateMultiplier), maxCandidates);
            sqlOffset = 0;
        }
        Set<Long> memberNamespaceIds = query.visibilityScope().memberNamespaceIds().isEmpty()
                ? Set.of(-1L)
                : query.visibilityScope().memberNamespaceIds();
        Set<Long> adminNamespaceIds = query.visibilityScope().adminNamespaceIds().isEmpty()
                ? Set.of(-1L)
                : query.visibilityScope().adminNamespaceIds();

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT d.skill_id ");
        sql.append("FROM skill_search_document d ");
        sql.append("JOIN skill s ON s.id = d.skill_id ");
        sql.append("JOIN namespace n ON n.id = d.namespace_id ");
        sql.append("WHERE 1=1 ");

        // Visibility filtering
        sql.append("AND (d.visibility = 'PUBLIC' ");
        if (query.visibilityScope().userId() != null) {
            sql.append("OR (d.visibility = 'NAMESPACE_ONLY' AND d.namespace_id IN :memberNamespaceIds) ");
            sql.append("OR (d.visibility = 'PRIVATE' AND (d.namespace_id IN :adminNamespaceIds OR d.owner_id = :userId)) ");
        }
        sql.append(") ");

        // Status filtering
        sql.append("AND d.status = 'ACTIVE' ");
        sql.append("AND s.status = 'ACTIVE' ");
        sql.append("AND s.hidden = FALSE ");
        sql.append("AND (n.status <> 'ARCHIVED' ");
        if (query.visibilityScope().userId() != null) {
            sql.append("OR d.namespace_id IN :memberNamespaceIds ");
        }
        sql.append(") ");

        // Namespace filtering
        if (query.namespaceId() != null) {
            sql.append("AND d.namespace_id = :namespaceId ");
        }

        // Full-text search
        if (hasKeyword) {
            sql.append("AND (");
            if (hasTsQuery) {
                if (useShortPrefixTitleSearch) {
                    sql.append(TITLE_VECTOR_SQL).append(" @@ to_tsquery('simple', :tsQuery) ");
                } else {
                    sql.append("d.search_vector @@ to_tsquery('simple', :tsQuery) ");
                }
                sql.append(" OR ");
            }
            sql.append(TITLE_SQL).append(" LIKE :titleLike");
            sql.append(") ");
        }

        // Sorting
        if ("downloads".equals(query.sortBy())) {
            sql.append("ORDER BY s.download_count DESC, s.updated_at DESC, d.skill_id DESC ");
        } else if ("rating".equals(query.sortBy())) {
            sql.append("ORDER BY s.rating_avg DESC, s.updated_at DESC, d.skill_id DESC ");
        } else if ("newest".equals(query.sortBy())) {
            sql.append("ORDER BY s.updated_at DESC, d.skill_id DESC ");
        } else if (useRelevanceOrdering) {
            sql.append("ORDER BY CASE ");
            sql.append("WHEN ").append(TITLE_SQL).append(" = :titleExact THEN 4 ");
            sql.append("WHEN ").append(TITLE_SQL).append(" LIKE :titlePrefix THEN 3 ");
            sql.append("WHEN ").append(TITLE_SQL).append(" LIKE :titleLike THEN 2 ");
            sql.append("ELSE 1 END DESC, ");
            if (useShortPrefixTitleSearch) {
                sql.append("ts_rank_cd(").append(TITLE_VECTOR_SQL)
                        .append(", to_tsquery('simple', :tsQuery)) DESC, d.updated_at DESC, d.skill_id DESC ");
            } else if (hasTsQuery) {
                sql.append("ts_rank_cd(d.search_vector, to_tsquery('simple', :tsQuery)) DESC, d.updated_at DESC, d.skill_id DESC ");
            } else {
                sql.append("d.updated_at DESC, d.skill_id DESC ");
            }
        } else {
            sql.append("ORDER BY s.updated_at DESC, d.skill_id DESC ");
        }

        // Pagination
        sql.append("LIMIT :limit OFFSET :offset");

        Query nativeQuery = entityManager.createNativeQuery(sql.toString());

        if (query.visibilityScope().userId() != null) {
            nativeQuery.setParameter("memberNamespaceIds", memberNamespaceIds);
            nativeQuery.setParameter("adminNamespaceIds", adminNamespaceIds);
            nativeQuery.setParameter("userId", query.visibilityScope().userId());
        }

        if (query.namespaceId() != null) {
            nativeQuery.setParameter("namespaceId", query.namespaceId());
        }

        if (hasKeyword) {
            if (hasTsQuery) {
                nativeQuery.setParameter("tsQuery", tsQuery);
            }
            if (useRelevanceOrdering) {
                nativeQuery.setParameter("titleExact", normalizedKeyword.toLowerCase());
                nativeQuery.setParameter("titlePrefix", normalizedKeyword.toLowerCase() + "%");
            }
            nativeQuery.setParameter("titleLike", "%" + normalizedKeyword.toLowerCase() + "%");
        }

        nativeQuery.setParameter("limit", sqlLimit);
        nativeQuery.setParameter("offset", sqlOffset);

        @SuppressWarnings("unchecked")
        List<Long> skillIds = (List<Long>) nativeQuery.getResultList().stream()
                .map(obj -> ((Number) obj).longValue())
                .toList();

        // Count total
        String countSql = sql.toString().replaceFirst("SELECT d\\.skill_id", "SELECT COUNT(*)");
        int orderByIndex = countSql.indexOf("ORDER BY");
        if (orderByIndex >= 0) {
            countSql = countSql.substring(0, orderByIndex);
        }
        int limitIndex = countSql.indexOf("LIMIT");
        if (limitIndex >= 0) {
            countSql = countSql.substring(0, limitIndex);
        }

        Query countQuery = entityManager.createNativeQuery(countSql);

        if (query.visibilityScope().userId() != null) {
            countQuery.setParameter("memberNamespaceIds", memberNamespaceIds);
            countQuery.setParameter("adminNamespaceIds", adminNamespaceIds);
            countQuery.setParameter("userId", query.visibilityScope().userId());
        }

        if (query.namespaceId() != null) {
            countQuery.setParameter("namespaceId", query.namespaceId());
        }

        if (hasKeyword) {
            if (hasTsQuery) {
                countQuery.setParameter("tsQuery", tsQuery);
            }
            countQuery.setParameter("titleLike", "%" + normalizedKeyword.toLowerCase() + "%");
        }

        long total = ((Number) countQuery.getSingleResult()).longValue();

        if (useSemanticRerank && !skillIds.isEmpty()) {
            skillIds = rerankBySemanticSimilarity(skillIds, normalizedKeyword, requestedOffset, query.size());
        }

        return new SearchResult(skillIds, total, query.page(), query.size());
    }

    private List<Long> rerankBySemanticSimilarity(List<Long> candidateSkillIds,
                                                  String normalizedKeyword,
                                                  int requestedOffset,
                                                  int pageSize) {
        Map<Long, SkillSearchDocumentEntity> documentsBySkillId = new HashMap<>();
        for (SkillSearchDocumentEntity entity : searchDocumentRepository.findBySkillIdIn(candidateSkillIds)) {
            documentsBySkillId.put(entity.getSkillId(), entity);
        }

        int totalCandidates = Math.max(candidateSkillIds.size(), 1);
        List<RankedSkill> rankedSkills = new java.util.ArrayList<>(candidateSkillIds.size());
        for (int index = 0; index < candidateSkillIds.size(); index++) {
            Long skillId = candidateSkillIds.get(index);
            SkillSearchDocumentEntity entity = documentsBySkillId.get(skillId);
            double baseScore = 1D - (index / (double) totalCandidates);
            double semanticScore = computeSemanticScore(normalizedKeyword, entity);
            double combinedScore = (baseScore * (1D - semanticWeight)) + (semanticScore * semanticWeight);
            rankedSkills.add(new RankedSkill(skillId, combinedScore));
        }

        return rankedSkills.stream()
                .sorted(Comparator.comparingDouble(RankedSkill::score).reversed())
                .skip(requestedOffset)
                .limit(pageSize)
                .map(RankedSkill::skillId)
                .toList();
    }

    private double computeSemanticScore(String normalizedKeyword, SkillSearchDocumentEntity entity) {
        if (entity == null) {
            return 0D;
        }
        String serializedVector = entity.getSemanticVector();
        if (serializedVector == null || serializedVector.isBlank()) {
            serializedVector = searchEmbeddingService.embed(String.join("\n",
                    safe(entity.getTitle()),
                    safe(entity.getSummary()),
                    safe(entity.getKeywords()),
                    safe(entity.getSearchText())));
        }
        return searchEmbeddingService.similarity(normalizedKeyword, serializedVector);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim().toLowerCase();
    }

    private String buildPrefixTsQuery(String keyword) {
        if (keyword == null) {
            return null;
        }

        List<String> terms = QUERY_TERM_SPLITTER.splitAsStream(keyword.toLowerCase())
                .map(String::trim)
                .filter(term -> !term.isBlank())
                .distinct()
                .limit(MAX_QUERY_TERMS)
                .toList();

        if (terms.isEmpty()) {
            return null;
        }

        List<String> tsQueryTerms = terms.stream()
                .filter(this::isTsQueryCompatibleTerm)
                .toList();

        if (tsQueryTerms.isEmpty()) {
            return null;
        }

        return tsQueryTerms.stream()
                .map(term -> term + ":*")
                .reduce((left, right) -> left + " & " + right)
                .orElse(null);
    }

    private boolean isTsQueryCompatibleTerm(String term) {
        return term.chars().anyMatch(ch -> Character.isLetter(ch) || ch == '_');
    }

    private record RankedSkill(Long skillId, double score) {
    }
}
