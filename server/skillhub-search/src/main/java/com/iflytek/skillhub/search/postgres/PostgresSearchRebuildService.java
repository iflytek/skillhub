package com.iflytek.skillhub.search.postgres;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.search.SearchIndexService;
import com.iflytek.skillhub.search.SearchRebuildService;
import com.iflytek.skillhub.search.SkillSearchDocument;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Reconstructs PostgreSQL search documents from canonical skill and namespace records.
 */
@Service
public class PostgresSearchRebuildService implements SearchRebuildService {
    private static final Set<String> RESERVED_FRONTMATTER_FIELDS = Set.of("name", "description", "version");
    private static final Set<String> KEYWORD_FIELD_NAMES = Set.of("keywords", "keyword", "tags", "tag");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final SkillRepository skillRepository;
    private final NamespaceRepository namespaceRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SearchIndexService searchIndexService;
    private final ObjectMapper objectMapper;

    public PostgresSearchRebuildService(
            SkillRepository skillRepository,
            NamespaceRepository namespaceRepository,
            SkillVersionRepository skillVersionRepository,
            SearchIndexService searchIndexService) {
        this.skillRepository = skillRepository;
        this.namespaceRepository = namespaceRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.searchIndexService = searchIndexService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void rebuildAll() {
        List<SkillSearchDocument> documents = skillRepository.findAll().stream()
                .filter(skill -> skill.getStatus() == SkillStatus.ACTIVE)
                .map(this::toDocument)
                .flatMap(Optional::stream)
                .toList();
        searchIndexService.batchIndex(documents);
    }

    @Override
    public void rebuildByNamespace(Long namespaceId) {
        List<Skill> skills = skillRepository.findByNamespaceIdAndStatus(namespaceId, SkillStatus.ACTIVE);

        for (Skill skill : skills) {
            rebuildBySkill(skill.getId());
        }
    }

    @Override
    public void rebuildBySkill(Long skillId) {
        Optional<Skill> skillOpt = skillRepository.findById(skillId);
        if (skillOpt.isEmpty()) {
            return;
        }

        toDocument(skillOpt.get()).ifPresent(searchIndexService::index);
    }

    private SearchIndexPayload buildSearchPayload(Skill skill) {
        List<String> searchParts = new ArrayList<>();
        addPart(searchParts, skill.getDisplayName());
        addPart(searchParts, skill.getSlug());
        addPart(searchParts, skill.getSummary());

        Set<String> keywords = new TreeSet<>();
        resolveLatestVersion(skill)
                .map(this::extractParsedMetadata)
                .map(metadata -> metadata.get("frontmatter"))
                .map(this::asMap)
                .ifPresent(frontmatter -> appendFrontmatter(frontmatter, keywords, searchParts));

        return new SearchIndexPayload(
                String.join(", ", keywords),
                String.join(" ", searchParts).trim()
        );
    }

    private Optional<SkillVersion> resolveLatestVersion(Skill skill) {
        if (skill.getLatestVersionId() == null) {
            return Optional.empty();
        }
        return skillVersionRepository.findById(skill.getLatestVersionId());
    }

    private Map<String, Object> extractParsedMetadata(SkillVersion version) {
        String metadataJson = version.getParsedMetadataJson();
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadataJson, MAP_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .filter(entry -> entry.getKey() != null)
                    .collect(Collectors.toMap(entry -> String.valueOf(entry.getKey()), Map.Entry::getValue));
        }
        return Map.of();
    }

    private void appendFrontmatter(Map<String, Object> frontmatter, Set<String> keywords, List<String> searchParts) {
        for (Map.Entry<String, Object> entry : frontmatter.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }

            if (KEYWORD_FIELD_NAMES.contains(fieldName.toLowerCase())) {
                flattenToStrings(value).forEach(keyword -> {
                    String normalized = keyword.trim();
                    if (!normalized.isBlank()) {
                        keywords.add(normalized);
                    }
                });
            }

            if (!RESERVED_FRONTMATTER_FIELDS.contains(fieldName.toLowerCase())) {
                addPart(searchParts, fieldName);
                flattenToStrings(value).forEach(text -> addPart(searchParts, text));
            }
        }
    }

    private List<String> flattenToStrings(Object value) {
        if (value instanceof String text) {
            return List.of(text);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return List.of(String.valueOf(value));
        }
        if (value instanceof Map<?, ?> map) {
            List<String> values = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    values.add(String.valueOf(entry.getKey()));
                }
                values.addAll(flattenToStrings(entry.getValue()));
            }
            return values;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(Objects::nonNull)
                    .flatMap(item -> flattenToStrings(item).stream())
                    .toList();
        }
        return List.of(String.valueOf(value));
    }

    private void addPart(List<String> parts, String value) {
        if (value == null) {
            return;
        }
        String normalized = value.trim();
        if (!normalized.isBlank()) {
            parts.add(normalized);
        }
    }

    private Optional<SkillSearchDocument> toDocument(Skill skill) {
        Optional<Namespace> namespaceOpt = namespaceRepository.findById(skill.getNamespaceId());
        if (namespaceOpt.isEmpty()) {
            return Optional.empty();
        }

        Namespace namespace = namespaceOpt.get();
        SearchIndexPayload payload = buildSearchPayload(skill);

        return Optional.of(new SkillSearchDocument(
                skill.getId(),
                skill.getNamespaceId(),
                namespace.getSlug(),
                skill.getOwnerId(),
                skill.getDisplayName() != null ? skill.getDisplayName() : skill.getSlug(),
                skill.getSummary(),
                payload.keywords(),
                payload.searchText(),
                null,
                skill.getVisibility().name(),
                skill.getStatus().name()
        ));
    }

    private record SearchIndexPayload(String keywords, String searchText) {
    }
}
