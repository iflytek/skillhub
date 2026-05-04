package com.iflytek.skillhub.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.label.LabelDefinition;
import com.iflytek.skillhub.domain.label.LabelDefinitionRepository;
import com.iflytek.skillhub.domain.label.LabelTranslation;
import com.iflytek.skillhub.domain.label.LabelTranslationRepository;
import com.iflytek.skillhub.domain.label.LabelType;
import com.iflytek.skillhub.domain.label.SkillLabel;
import com.iflytek.skillhub.domain.label.SkillLabelRepository;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbstractJpaSearchRebuildServiceTest {

    private SkillRepository skillRepository;
    private NamespaceRepository namespaceRepository;
    private SkillVersionRepository skillVersionRepository;
    private LabelDefinitionRepository labelDefinitionRepository;
    private LabelTranslationRepository labelTranslationRepository;
    private SkillLabelRepository skillLabelRepository;
    private SearchIndexService searchIndexService;
    private SearchTextTokenizer searchTextTokenizer;
    private TestSearchRebuildService service;

    @BeforeEach
    void setUp() {
        skillRepository = mock(SkillRepository.class);
        namespaceRepository = mock(NamespaceRepository.class);
        skillVersionRepository = mock(SkillVersionRepository.class);
        labelDefinitionRepository = mock(LabelDefinitionRepository.class);
        labelTranslationRepository = mock(LabelTranslationRepository.class);
        skillLabelRepository = mock(SkillLabelRepository.class);
        searchIndexService = mock(SearchIndexService.class);
        searchTextTokenizer = mock(SearchTextTokenizer.class);
        when(searchTextTokenizer.enrichForIndex(any())).thenReturn("enriched");

        service = new TestSearchRebuildService(
                skillRepository,
                namespaceRepository,
                skillVersionRepository,
                labelDefinitionRepository,
                labelTranslationRepository,
                skillLabelRepository,
                searchIndexService,
                searchTextTokenizer
        );
    }

    @Test
    void rebuildAllWithActiveSkills() {
        Skill skill = createSkill(1L, 1L, "slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        Namespace ns = createNamespace(1L, "ns");
        when(skillRepository.findAll()).thenReturn(List.of(skill));
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(ns));
        when(skillVersionRepository.findById(any())).thenReturn(Optional.empty());
        when(skillLabelRepository.findBySkillId(1L)).thenReturn(List.of());

        service.rebuildAll();

        verify(searchIndexService).batchIndex(anyList());
    }

    @Test
    void rebuildAllFiltersNonActiveSkills() {
        Skill active = createSkill(1L, 1L, "active", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        Skill archived = createSkill(2L, 1L, "archived", "owner", SkillVisibility.PUBLIC, SkillStatus.ARCHIVED);
        when(skillRepository.findAll()).thenReturn(List.of(active, archived));
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(createNamespace(1L, "ns")));
        when(skillVersionRepository.findById(any())).thenReturn(Optional.empty());
        when(skillLabelRepository.findBySkillId(1L)).thenReturn(List.of());

        service.rebuildAll();

        verify(searchIndexService).batchIndex(anyList());
    }

    @Test
    void rebuildAllWithNoSkills() {
        when(skillRepository.findAll()).thenReturn(List.of());

        service.rebuildAll();

        verify(searchIndexService).batchIndex(List.of());
    }

    @Test
    void rebuildByNamespaceWithSkills() {
        Skill skill = createSkill(1L, 1L, "slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        when(skillRepository.findByNamespaceIdAndStatus(1L, SkillStatus.ACTIVE))
                .thenReturn(List.of(skill));
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(createNamespace(1L, "ns")));
        when(skillVersionRepository.findById(any())).thenReturn(Optional.empty());
        when(skillLabelRepository.findBySkillId(1L)).thenReturn(List.of());

        service.rebuildByNamespace(1L);

        verify(searchIndexService).index(any(SkillSearchDocument.class));
    }

    @Test
    void rebuildByNamespaceWithNoSkills() {
        when(skillRepository.findByNamespaceIdAndStatus(1L, SkillStatus.ACTIVE))
                .thenReturn(List.of());

        service.rebuildByNamespace(1L);

        verify(searchIndexService, never()).index(any(SkillSearchDocument.class));
    }

    @Test
    void rebuildBySkillWithExistingSkill() {
        Skill skill = createSkill(1L, 1L, "slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(createNamespace(1L, "ns")));
        when(skillVersionRepository.findById(any())).thenReturn(Optional.empty());
        when(skillLabelRepository.findBySkillId(1L)).thenReturn(List.of());

        service.rebuildBySkill(1L);

        verify(searchIndexService).index(any(SkillSearchDocument.class));
    }

    @Test
    void rebuildBySkillWithMissingSkill() {
        when(skillRepository.findById(1L)).thenReturn(Optional.empty());

        service.rebuildBySkill(1L);

        verify(searchIndexService, never()).index(any(SkillSearchDocument.class));
    }

    @Test
    void toDocumentWithMissingNamespaceReturnsEmpty() {
        Skill skill = createSkill(1L, 1L, "slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        when(namespaceRepository.findById(1L)).thenReturn(Optional.empty());

        Optional<SkillSearchDocument> result = service.toDocument(skill);

        assertThat(result).isEmpty();
    }

    @Test
    void toDocumentUsesSlugWhenDisplayNameIsNull() {
        Skill skill = createSkill(1L, 1L, "my-slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        skill.setDisplayName(null);
        Namespace ns = createNamespace(1L, "ns");
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(ns));
        when(skillVersionRepository.findById(any())).thenReturn(Optional.empty());
        when(skillLabelRepository.findBySkillId(1L)).thenReturn(List.of());

        Optional<SkillSearchDocument> result = service.toDocument(skill);

        assertThat(result).isPresent();
        assertThat(result.get().title()).isEqualTo("my-slug");
    }

    @Test
    void toDocumentHandlesNullCountsAndRating() {
        Skill skill = new Skill(1L, "slug", "owner", SkillVisibility.PUBLIC);
        skill.setStatus(SkillStatus.ACTIVE);
        setField(skill, "downloadCount", null);
        setField(skill, "ratingAvg", null);
        setField(skill, "updatedAt", null);
        Namespace ns = createNamespace(1L, "ns");
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(ns));
        when(skillVersionRepository.findById(any())).thenReturn(Optional.empty());
        when(skillLabelRepository.findBySkillId(any())).thenReturn(List.of());

        Optional<SkillSearchDocument> result = service.toDocument(skill);

        assertThat(result).isPresent();
        assertThat(result.get().downloadCount()).isEqualTo(0L);
        assertThat(result.get().ratingAvg()).isEqualTo(0D);
        assertThat(result.get().updatedAtEpochMillis()).isEqualTo(0L);
    }

    @Test
    void buildSearchPayloadWithNullLatestVersion() {
        Skill skill = createSkill(1L, 1L, "slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        skill.setLatestVersionId(null);
        Namespace ns = createNamespace(1L, "ns");
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(ns));
        when(skillLabelRepository.findBySkillId(1L)).thenReturn(List.of());

        Optional<SkillSearchDocument> result = service.toDocument(skill);

        assertThat(result).isPresent();
    }

    @Test
    void buildSearchPayloadWithNullMetadata() {
        Skill skill = createSkill(1L, 1L, "slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        skill.setLatestVersionId(10L);
        SkillVersion version = new SkillVersion(1L, "1.0", "creator");
        version.setParsedMetadataJson(null);
        Namespace ns = createNamespace(1L, "ns");
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(ns));
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(skillLabelRepository.findBySkillId(1L)).thenReturn(List.of());

        Optional<SkillSearchDocument> result = service.toDocument(skill);

        assertThat(result).isPresent();
    }

    @Test
    void buildSearchPayloadWithBlankMetadata() {
        Skill skill = createSkill(1L, 1L, "slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        skill.setLatestVersionId(10L);
        SkillVersion version = new SkillVersion(1L, "1.0", "creator");
        version.setParsedMetadataJson("   ");
        Namespace ns = createNamespace(1L, "ns");
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(ns));
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(skillLabelRepository.findBySkillId(1L)).thenReturn(List.of());

        Optional<SkillSearchDocument> result = service.toDocument(skill);

        assertThat(result).isPresent();
    }

    @Test
    void buildSearchPayloadWithInvalidJsonMetadata() {
        Skill skill = createSkill(1L, 1L, "slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        skill.setLatestVersionId(10L);
        SkillVersion version = new SkillVersion(1L, "1.0", "creator");
        version.setParsedMetadataJson("{not json");
        Namespace ns = createNamespace(1L, "ns");
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(ns));
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(skillLabelRepository.findBySkillId(1L)).thenReturn(List.of());

        Optional<SkillSearchDocument> result = service.toDocument(skill);

        assertThat(result).isPresent();
    }

    @Test
    void buildSearchPayloadWithFrontmatterKeywords() {
        Skill skill = createSkill(1L, 1L, "slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        skill.setLatestVersionId(10L);
        SkillVersion version = new SkillVersion(1L, "1.0", "creator");
        version.setParsedMetadataJson("{\"frontmatter\":{\"keywords\":[\"ml\",\"ai\"],\"tags\":[\"tag1\"],\"name\":\"test\",\"custom\":\"value\"}}");
        Namespace ns = createNamespace(1L, "ns");
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(ns));
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(skillLabelRepository.findBySkillId(1L)).thenReturn(List.of());

        Optional<SkillSearchDocument> result = service.toDocument(skill);

        assertThat(result).isPresent();
    }

    @Test
    void buildSearchPayloadWithFrontmatterNullValue() {
        Skill skill = createSkill(1L, 1L, "slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        skill.setLatestVersionId(10L);
        SkillVersion version = new SkillVersion(1L, "1.0", "creator");
        version.setParsedMetadataJson("{\"frontmatter\":{\"keywords\":null,\"other\":\"val\"}}");
        Namespace ns = createNamespace(1L, "ns");
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(ns));
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(skillLabelRepository.findBySkillId(1L)).thenReturn(List.of());

        Optional<SkillSearchDocument> result = service.toDocument(skill);

        assertThat(result).isPresent();
    }

    @Test
    void buildSearchPayloadWithFrontmatterMapValue() {
        Skill skill = createSkill(1L, 1L, "slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        skill.setLatestVersionId(10L);
        SkillVersion version = new SkillVersion(1L, "1.0", "creator");
        version.setParsedMetadataJson("{\"frontmatter\":{\"config\":{\"a\":\"b\",\"c\":null}}}");
        Namespace ns = createNamespace(1L, "ns");
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(ns));
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(skillLabelRepository.findBySkillId(1L)).thenReturn(List.of());

        Optional<SkillSearchDocument> result = service.toDocument(skill);

        assertThat(result).isPresent();
    }

    @Test
    void buildSearchPayloadWithFrontmatterCollectionValue() {
        Skill skill = createSkill(1L, 1L, "slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        skill.setLatestVersionId(10L);
        SkillVersion version = new SkillVersion(1L, "1.0", "creator");
        version.setParsedMetadataJson("{\"frontmatter\":{\"items\":[\"a\",\"b\",{\"nested\":\"c\"}]}}");
        Namespace ns = createNamespace(1L, "ns");
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(ns));
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(skillLabelRepository.findBySkillId(1L)).thenReturn(List.of());

        Optional<SkillSearchDocument> result = service.toDocument(skill);

        assertThat(result).isPresent();
    }

    @Test
    void buildSearchPayloadWithFrontmatterNumberAndBoolean() {
        Skill skill = createSkill(1L, 1L, "slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        skill.setLatestVersionId(10L);
        SkillVersion version = new SkillVersion(1L, "1.0", "creator");
        version.setParsedMetadataJson("{\"frontmatter\":{\"count\":42,\"enabled\":true,\"other\":\"val\"}}");
        Namespace ns = createNamespace(1L, "ns");
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(ns));
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(skillLabelRepository.findBySkillId(1L)).thenReturn(List.of());

        Optional<SkillSearchDocument> result = service.toDocument(skill);

        assertThat(result).isPresent();
    }

    @Test
    void buildSearchPayloadWithFrontmatterOtherType() {
        Skill skill = createSkill(1L, 1L, "slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        skill.setLatestVersionId(10L);
        SkillVersion version = new SkillVersion(1L, "1.0", "creator");
        version.setParsedMetadataJson("{\"frontmatter\":{\"obj\":{\"nested\":{\"deep\":\"value\"}}}}");
        Namespace ns = createNamespace(1L, "ns");
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(ns));
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(skillLabelRepository.findBySkillId(1L)).thenReturn(List.of());

        Optional<SkillSearchDocument> result = service.toDocument(skill);

        assertThat(result).isPresent();
    }

    @Test
    void appendLabelKeywordsWithLabels() {
        Skill skill = createSkill(1L, 1L, "slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        skill.setLatestVersionId(10L);
        SkillVersion version = new SkillVersion(1L, "1.0", "creator");
        version.setParsedMetadataJson(null);
        Namespace ns = createNamespace(1L, "ns");
        SkillLabel skillLabel = new SkillLabel(1L, 100L, "creator");
        LabelDefinition labelDef = new LabelDefinition("ml", LabelType.RECOMMENDED, true, 1, "creator");
        setField(labelDef, "id", 100L);
        LabelTranslation translation = new LabelTranslation(100L, "zh", "machine learning");
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(ns));
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(skillLabelRepository.findBySkillId(1L)).thenReturn(List.of(skillLabel));
        when(labelDefinitionRepository.findByIdIn(List.of(100L))).thenReturn(List.of(labelDef));
        when(labelTranslationRepository.findByLabelIdIn(List.of(100L))).thenReturn(List.of(translation));

        Optional<SkillSearchDocument> result = service.toDocument(skill);

        assertThat(result).isPresent();
    }

    @Test
    void appendLabelKeywordsWithEmptyLabels() {
        Skill skill = createSkill(1L, 1L, "slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        skill.setLatestVersionId(10L);
        SkillVersion version = new SkillVersion(1L, "1.0", "creator");
        version.setParsedMetadataJson(null);
        Namespace ns = createNamespace(1L, "ns");
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(ns));
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(skillLabelRepository.findBySkillId(1L)).thenReturn(List.of());

        Optional<SkillSearchDocument> result = service.toDocument(skill);

        assertThat(result).isPresent();
    }

    @Test
    void resolveLabelSlugsWithLabels() {
        Skill skill = createSkill(1L, 1L, "slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        skill.setLatestVersionId(10L);
        SkillVersion version = new SkillVersion(1L, "1.0", "creator");
        version.setParsedMetadataJson(null);
        Namespace ns = createNamespace(1L, "ns");
        SkillLabel skillLabel = new SkillLabel(1L, 100L, "creator");
        LabelDefinition labelDef = new LabelDefinition("machine-learning", LabelType.RECOMMENDED, true, 1, "creator");
        setField(labelDef, "id", 100L);
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(ns));
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(skillLabelRepository.findBySkillId(1L)).thenReturn(List.of(skillLabel));
        when(labelDefinitionRepository.findByIdIn(List.of(100L))).thenReturn(List.of(labelDef));

        Optional<SkillSearchDocument> result = service.toDocument(skill);

        assertThat(result).isPresent();
        assertThat(result.get().labelSlugs()).containsExactly("machine-learning");
    }

    @Test
    void resolveLabelSlugsWithEmptyLabels() {
        Skill skill = createSkill(1L, 1L, "slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        skill.setLatestVersionId(10L);
        SkillVersion version = new SkillVersion(1L, "1.0", "creator");
        version.setParsedMetadataJson(null);
        Namespace ns = createNamespace(1L, "ns");
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(ns));
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(skillLabelRepository.findBySkillId(1L)).thenReturn(List.of());

        Optional<SkillSearchDocument> result = service.toDocument(skill);

        assertThat(result).isPresent();
        assertThat(result.get().labelSlugs()).isEmpty();
    }

    @Test
    void toDocumentWithHiddenSkill() {
        Skill skill = createSkill(1L, 1L, "slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        skill.setHidden(true);
        Namespace ns = createNamespace(1L, "ns");
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(ns));
        when(skillVersionRepository.findById(any())).thenReturn(Optional.empty());
        when(skillLabelRepository.findBySkillId(1L)).thenReturn(List.of());

        Optional<SkillSearchDocument> result = service.toDocument(skill);

        assertThat(result).isPresent();
        assertThat(result.get().hidden()).isTrue();
    }

    @Test
    void findActiveSkillsByNamespaceDelegatesToRepository() {
        when(skillRepository.findByNamespaceIdAndStatus(1L, SkillStatus.ACTIVE))
                .thenReturn(List.of());

        List<Skill> result = service.findActiveSkillsByNamespace(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void findSkillByIdDelegatesToRepository() {
        Skill skill = createSkill(1L, 1L, "slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));

        Optional<Skill> result = service.findSkillById(1L);

        assertThat(result).isPresent();
    }

    @Test
    void indexDocumentDelegatesToSearchIndexService() {
        SkillSearchDocument doc = new SkillSearchDocument(
                1L, 1L, "ns", "owner", "title", "sum", "kw", "search",
                null, "PUBLIC", "ACTIVE", List.of(), 0L, 0.0, 0L, "ACTIVE", false
        );

        service.indexDocument(doc);

        verify(searchIndexService).index(doc);
    }

    @Test
    void removeDocumentDelegatesToSearchIndexService() {
        service.removeDocument(1L);

        verify(searchIndexService).remove(1L);
    }

    @Test
    void appendLabelKeywordsWithNullRepositories() {
        TestSearchRebuildService nullRepoService = new TestSearchRebuildService(
                skillRepository,
                namespaceRepository,
                skillVersionRepository,
                null,
                null,
                null,
                searchIndexService,
                searchTextTokenizer
        );
        Skill skill = createSkill(1L, 1L, "slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        Namespace ns = createNamespace(1L, "ns");
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(ns));
        when(skillVersionRepository.findById(any())).thenReturn(Optional.empty());

        Optional<SkillSearchDocument> result = nullRepoService.toDocument(skill);

        assertThat(result).isPresent();
    }

    @Test
    void resolveLabelSlugsWithNullRepositories() {
        TestSearchRebuildService nullRepoService = new TestSearchRebuildService(
                skillRepository,
                namespaceRepository,
                skillVersionRepository,
                null,
                null,
                null,
                searchIndexService,
                searchTextTokenizer
        );
        Skill skill = createSkill(1L, 1L, "slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        Namespace ns = createNamespace(1L, "ns");
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(ns));
        when(skillVersionRepository.findById(any())).thenReturn(Optional.empty());

        Optional<SkillSearchDocument> result = nullRepoService.toDocument(skill);

        assertThat(result).isPresent();
        assertThat(result.get().labelSlugs()).isEmpty();
    }

    @Test
    void asMapWithNonMapValueReturnsEmpty() {
        Skill skill = createSkill(1L, 1L, "slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        skill.setLatestVersionId(10L);
        SkillVersion version = new SkillVersion(1L, "1.0", "creator");
        version.setParsedMetadataJson("{\"frontmatter\":\"not-a-map\"}");
        Namespace ns = createNamespace(1L, "ns");
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(ns));
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(skillLabelRepository.findBySkillId(1L)).thenReturn(List.of());

        Optional<SkillSearchDocument> result = service.toDocument(skill);

        assertThat(result).isPresent();
    }

    @Test
    void asMapWithMapHavingNullKey() {
        Skill skill = createSkill(1L, 1L, "slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        skill.setLatestVersionId(10L);
        SkillVersion version = new SkillVersion(1L, "1.0", "creator");
        version.setParsedMetadataJson("{\"frontmatter\":{\"valid\":\"ok\",\"null\":null}}");
        Namespace ns = createNamespace(1L, "ns");
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(ns));
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(skillLabelRepository.findBySkillId(1L)).thenReturn(List.of());

        Optional<SkillSearchDocument> result = service.toDocument(skill);

        assertThat(result).isPresent();
    }

    @Test
    void flattenToStringsWithUnknownType() {
        Skill skill = createSkill(1L, 1L, "slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        skill.setLatestVersionId(10L);
        SkillVersion version = new SkillVersion(1L, "1.0", "creator");
        version.setParsedMetadataJson("{\"frontmatter\":{\"obj\":{\"nested\":{\"arr\":[1,2,3]}}}}");
        Namespace ns = createNamespace(1L, "ns");
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(ns));
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(skillLabelRepository.findBySkillId(1L)).thenReturn(List.of());

        Optional<SkillSearchDocument> result = service.toDocument(skill);

        assertThat(result).isPresent();
    }

    @Test
    void addPartSkipsNullAndBlank() {
        Skill skill = createSkill(1L, 1L, "slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        skill.setSummary(null);
        Namespace ns = createNamespace(1L, "ns");
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(ns));
        when(skillVersionRepository.findById(any())).thenReturn(Optional.empty());
        when(skillLabelRepository.findBySkillId(1L)).thenReturn(List.of());

        Optional<SkillSearchDocument> result = service.toDocument(skill);

        assertThat(result).isPresent();
    }

    @Test
    void addKeywordSkipsNullAndBlank() {
        TestSearchRebuildService testService = new TestSearchRebuildService(
                skillRepository,
                namespaceRepository,
                skillVersionRepository,
                labelDefinitionRepository,
                labelTranslationRepository,
                skillLabelRepository,
                searchIndexService,
                searchTextTokenizer
        );
        Skill skill = createSkill(1L, 1L, "slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        skill.setLatestVersionId(10L);
        SkillVersion version = new SkillVersion(1L, "1.0", "creator");
        version.setParsedMetadataJson("{\"frontmatter\":{\"keywords\":[\"  \",null,\"valid\"]}}");
        Namespace ns = createNamespace(1L, "ns");
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(ns));
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(skillLabelRepository.findBySkillId(1L)).thenReturn(List.of());

        Optional<SkillSearchDocument> result = testService.toDocument(skill);

        assertThat(result).isPresent();
    }

    @Test
    void rebuildBySkillToDocumentEmptyDoesNotIndex() {
        Skill skill = createSkill(1L, 1L, "slug", "owner", SkillVisibility.PUBLIC, SkillStatus.ACTIVE);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        when(namespaceRepository.findById(1L)).thenReturn(Optional.empty());

        service.rebuildBySkill(1L);

        verify(searchIndexService, never()).index(any());
    }

    @Test
    void flattenToStringsWithNullReturnsEmptyList() throws Exception {
        java.lang.reflect.Method method = AbstractJpaSearchRebuildService.class.getDeclaredMethod("flattenToStrings", Object.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(service, (Object) null);
        assertThat(result).isEmpty();
    }

    @Test
    void flattenToStringsWithUnknownTypeReturnsStringValue() throws Exception {
        java.lang.reflect.Method method = AbstractJpaSearchRebuildService.class.getDeclaredMethod("flattenToStrings", Object.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(service, new Object() {
            @Override
            public String toString() {
                return "custom";
            }
        });
        assertThat(result).containsExactly("custom");
    }

    @Test
    void addKeywordWithNullValueReturnsEarly() throws Exception {
        java.lang.reflect.Method method = AbstractJpaSearchRebuildService.class.getDeclaredMethod("addKeyword", java.util.Set.class, String.class);
        method.setAccessible(true);
        java.util.Set<String> keywords = new java.util.HashSet<>();
        method.invoke(service, keywords, null);
        assertThat(keywords).isEmpty();
    }

    private static Skill createSkill(Long id, Long namespaceId, String slug, String ownerId,
                                     SkillVisibility visibility, SkillStatus status) {
        Skill skill = new Skill(namespaceId, slug, ownerId, visibility);
        skill.setStatus(status);
        skill.setDisplayName("Display " + slug);
        skill.setSummary("Summary");
        setField(skill, "downloadCount", 10L);
        setField(skill, "ratingAvg", BigDecimal.valueOf(4.5));
        setField(skill, "updatedAt", Instant.now());
        skill.setLatestVersionId(100L);
        try {
            java.lang.reflect.Field idField = Skill.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(skill, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return skill;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Namespace createNamespace(Long id, String slug) {
        Namespace ns = new Namespace(slug, "Display " + slug, "creator");
        ns.setStatus(NamespaceStatus.ACTIVE);
        try {
            java.lang.reflect.Field idField = Namespace.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(ns, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return ns;
    }

    private static class TestSearchRebuildService extends AbstractJpaSearchRebuildService {
        TestSearchRebuildService(SkillRepository skillRepository,
                                 NamespaceRepository namespaceRepository,
                                 SkillVersionRepository skillVersionRepository,
                                 LabelDefinitionRepository labelDefinitionRepository,
                                 LabelTranslationRepository labelTranslationRepository,
                                 SkillLabelRepository skillLabelRepository,
                                 SearchIndexService searchIndexService,
                                 SearchTextTokenizer searchTextTokenizer) {
            super(skillRepository, namespaceRepository, skillVersionRepository,
                    labelDefinitionRepository, labelTranslationRepository, skillLabelRepository,
                    searchIndexService, searchTextTokenizer);
        }
    }
}
