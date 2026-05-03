package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.reset;

import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.event.SkillStatusChangedEvent;
import com.iflytek.skillhub.domain.label.LabelDefinitionRepository;
import com.iflytek.skillhub.domain.label.LabelTranslationRepository;
import com.iflytek.skillhub.domain.label.SkillLabelRepository;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillHardDeleteService;
import com.iflytek.skillhub.search.SearchTextTokenizer;
import com.iflytek.skillhub.search.event.SearchIndexEventListener;
import com.iflytek.skillhub.search.localfile.LocalFileIndexRebuildService;
import com.iflytek.skillhub.search.localfile.LocalFileIndexService;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileIndexLifecycleIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void publishArchiveAndDeleteShouldUpdateLuceneThroughRuntimeServices() throws Exception {
        SkillRepository skillRepository = mock(SkillRepository.class);
        NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
        SkillVersionRepository skillVersionRepository = mock(SkillVersionRepository.class);
        LabelDefinitionRepository labelDefinitionRepository = mock(LabelDefinitionRepository.class);
        LabelTranslationRepository labelTranslationRepository = mock(LabelTranslationRepository.class);
        SkillLabelRepository skillLabelRepository = mock(SkillLabelRepository.class);
        SkillHardDeleteService skillHardDeleteService = mock(SkillHardDeleteService.class);

        when(labelDefinitionRepository.findByIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
        when(labelTranslationRepository.findByLabelIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
        when(skillLabelRepository.findBySkillId(org.mockito.ArgumentMatchers.anyLong())).thenReturn(List.of());

        Skill skill = new Skill(10L, "smart-agent", "owner-1", SkillVisibility.PUBLIC);
        setField(skill, "id", 1L);
        skill.setDisplayName("Smart Agent");
        skill.setSummary("Builds workflows");
        skill.setLatestVersionId(99L);

        Namespace namespace = new Namespace("global", "Global", "system");
        setField(namespace, "id", 10L);

        SkillVersion version = new SkillVersion(1L, "1.0.0", "owner-1");
        version.setParsedMetadataJson("""
                {
                  "frontmatter": {
                    "keywords": ["automation"]
                  }
                }
                """);

        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        when(namespaceRepository.findById(10L)).thenReturn(Optional.of(namespace));
        when(skillVersionRepository.findById(99L)).thenReturn(Optional.of(version));

        LocalFileIndexService indexService = new LocalFileIndexService(tempDir);
        LocalFileIndexRebuildService rebuildService = new LocalFileIndexRebuildService(
                skillRepository,
                namespaceRepository,
                skillVersionRepository,
                labelDefinitionRepository,
                labelTranslationRepository,
                skillLabelRepository,
                indexService,
                new SearchTextTokenizer(),
                tempDir
        );
        SearchIndexEventListener listener = new SearchIndexEventListener(rebuildService, indexService);
        SkillDeleteAppService deleteAppService = new SkillDeleteAppService(
                skillRepository,
                namespaceRepository,
                skillHardDeleteService,
                indexService
        );

        listener.onSkillPublished(new SkillPublishedEvent(1L, 99L, "publisher-1"));
        assertThat(docCount()).isEqualTo(1);
        assertThat(storedField("1", "title")).isEqualTo("Smart Agent");

        listener.onSkillStatusChanged(new SkillStatusChangedEvent(1L, SkillStatus.ACTIVE, SkillStatus.ARCHIVED));
        assertThat(docCount()).isZero();

        listener.onSkillPublished(new SkillPublishedEvent(1L, 99L, "publisher-1"));
        assertThat(docCount()).isEqualTo(1);

        SkillDeleteAppService.DeleteResult result = deleteAppService.deleteSkillById(
                1L,
                "deleter-1",
                new AuditRequestContext("127.0.0.1", "JUnit")
        );

        assertThat(result.deleted()).isTrue();
        assertThat(docCount()).isZero();
        verify(skillHardDeleteService).hardDeleteSkill(skill, "global", "deleter-1", "127.0.0.1", "JUnit");
    }

    @Test
    void rebuildAllShouldResetLuceneDirectoryBeforeReindexingActiveSkills() throws Exception {
        SkillRepository skillRepository = mock(SkillRepository.class);
        NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
        SkillVersionRepository skillVersionRepository = mock(SkillVersionRepository.class);
        LabelDefinitionRepository labelDefinitionRepository = mock(LabelDefinitionRepository.class);
        LabelTranslationRepository labelTranslationRepository = mock(LabelTranslationRepository.class);
        SkillLabelRepository skillLabelRepository = mock(SkillLabelRepository.class);

        when(labelDefinitionRepository.findByIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
        when(labelTranslationRepository.findByLabelIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
        when(skillLabelRepository.findBySkillId(org.mockito.ArgumentMatchers.anyLong())).thenReturn(List.of());

        LocalFileIndexService indexService = new LocalFileIndexService(tempDir);
        LocalFileIndexRebuildService rebuildService = new LocalFileIndexRebuildService(
                skillRepository,
                namespaceRepository,
                skillVersionRepository,
                labelDefinitionRepository,
                labelTranslationRepository,
                skillLabelRepository,
                indexService,
                new SearchTextTokenizer(),
                tempDir
        );

        Skill staleSkill = skill(1L, 10L, "legacy-agent", "Legacy Agent", "Deprecated skill", 101L);
        Namespace namespace = namespace(10L, "global");
        SkillVersion staleVersion = version(101L, "legacy");

        when(skillRepository.findAll()).thenReturn(List.of(staleSkill));
        when(namespaceRepository.findById(10L)).thenReturn(Optional.of(namespace));
        when(skillVersionRepository.findById(101L)).thenReturn(Optional.of(staleVersion));

        rebuildService.rebuildAll();
        assertThat(docCount()).isEqualTo(1);
        assertThat(hitCount("1")).isEqualTo(1);

        Skill freshSkill = skill(2L, 10L, "fresh-agent", "Fresh Agent", "Current skill", 202L);
        SkillVersion freshVersion = version(202L, "fresh");
        reset(skillRepository, skillVersionRepository);
        when(skillRepository.findAll()).thenReturn(List.of(freshSkill));
        when(skillVersionRepository.findById(202L)).thenReturn(Optional.of(freshVersion));
        when(namespaceRepository.findById(10L)).thenReturn(Optional.of(namespace));

        rebuildService.rebuildAll();

        assertThat(docCount()).isEqualTo(1);
        assertThat(hitCount("1")).isZero();
        assertThat(hitCount("2")).isEqualTo(1);
        assertThat(storedField("2", "title")).isEqualTo("Fresh Agent");
    }

    @Test
    void rebuildByNamespaceShouldRefreshOnlyTargetNamespaceDocuments() throws Exception {
        SkillRepository skillRepository = mock(SkillRepository.class);
        NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
        SkillVersionRepository skillVersionRepository = mock(SkillVersionRepository.class);
        LabelDefinitionRepository labelDefinitionRepository = mock(LabelDefinitionRepository.class);
        LabelTranslationRepository labelTranslationRepository = mock(LabelTranslationRepository.class);
        SkillLabelRepository skillLabelRepository = mock(SkillLabelRepository.class);

        when(labelDefinitionRepository.findByIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
        when(labelTranslationRepository.findByLabelIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
        when(skillLabelRepository.findBySkillId(org.mockito.ArgumentMatchers.anyLong())).thenReturn(List.of());

        LocalFileIndexService indexService = new LocalFileIndexService(tempDir);
        LocalFileIndexRebuildService rebuildService = new LocalFileIndexRebuildService(
                skillRepository,
                namespaceRepository,
                skillVersionRepository,
                labelDefinitionRepository,
                labelTranslationRepository,
                skillLabelRepository,
                indexService,
                new SearchTextTokenizer(),
                tempDir
        );

        Namespace globalNamespace = namespace(10L, "global");
        Namespace teamNamespace = namespace(20L, "team-ai");
        Skill globalSkill = skill(1L, 10L, "global-agent", "Global Agent", "Updated global skill", 101L);
        Skill teamSkill = skill(2L, 20L, "team-agent", "Team Agent", "Unrelated team skill", 202L);
        SkillVersion globalVersion = version(101L, "global");
        SkillVersion teamVersion = version(202L, "team");

        when(namespaceRepository.findById(10L)).thenReturn(Optional.of(globalNamespace));
        when(namespaceRepository.findById(20L)).thenReturn(Optional.of(teamNamespace));
        when(skillVersionRepository.findById(101L)).thenReturn(Optional.of(globalVersion));
        when(skillVersionRepository.findById(202L)).thenReturn(Optional.of(teamVersion));
        when(skillRepository.findById(1L)).thenReturn(Optional.of(globalSkill));
        when(skillRepository.findById(2L)).thenReturn(Optional.of(teamSkill));

        rebuildService.rebuildBySkill(1L);
        rebuildService.rebuildBySkill(2L);

        assertThat(docCount()).isEqualTo(2);
        assertThat(storedField("1", "title")).isEqualTo("Global Agent");
        assertThat(storedField("2", "title")).isEqualTo("Team Agent");

        Skill renamedGlobalSkill = skill(1L, 10L, "global-agent", "Global Agent v2", "Retitled global skill", 303L);
        SkillVersion renamedGlobalVersion = version(303L, "retitled");

        reset(skillRepository, skillVersionRepository);
        when(skillRepository.findByNamespaceIdAndStatus(10L, SkillStatus.ACTIVE)).thenReturn(List.of(renamedGlobalSkill));
        when(namespaceRepository.findById(10L)).thenReturn(Optional.of(globalNamespace));
        when(namespaceRepository.findById(20L)).thenReturn(Optional.of(teamNamespace));
        when(skillVersionRepository.findById(303L)).thenReturn(Optional.of(renamedGlobalVersion));
        when(skillVersionRepository.findById(202L)).thenReturn(Optional.of(teamVersion));

        rebuildService.rebuildByNamespace(10L);

        assertThat(docCount()).isEqualTo(2);
        assertThat(storedField("1", "title")).isEqualTo("Global Agent v2");
        assertThat(storedField("2", "title")).isEqualTo("Team Agent");

        reset(skillRepository);
        when(skillRepository.findByNamespaceIdAndStatus(10L, SkillStatus.ACTIVE)).thenReturn(List.of());

        rebuildService.rebuildByNamespace(10L);

        assertThat(docCount()).isEqualTo(1);
        assertThat(hitCount("1")).isZero();
        assertThat(hitCount("2")).isEqualTo(1);
        assertThat(storedField("2", "title")).isEqualTo("Team Agent");
    }

    @Test
    void rebuildBySkillShouldReplaceOrRemoveOnlyTargetSkillDocument() throws Exception {
        SkillRepository skillRepository = mock(SkillRepository.class);
        NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
        SkillVersionRepository skillVersionRepository = mock(SkillVersionRepository.class);
        LabelDefinitionRepository labelDefinitionRepository = mock(LabelDefinitionRepository.class);
        LabelTranslationRepository labelTranslationRepository = mock(LabelTranslationRepository.class);
        SkillLabelRepository skillLabelRepository = mock(SkillLabelRepository.class);

        when(labelDefinitionRepository.findByIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
        when(labelTranslationRepository.findByLabelIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
        when(skillLabelRepository.findBySkillId(org.mockito.ArgumentMatchers.anyLong())).thenReturn(List.of());

        LocalFileIndexService indexService = new LocalFileIndexService(tempDir);
        LocalFileIndexRebuildService rebuildService = new LocalFileIndexRebuildService(
                skillRepository,
                namespaceRepository,
                skillVersionRepository,
                labelDefinitionRepository,
                labelTranslationRepository,
                skillLabelRepository,
                indexService,
                new SearchTextTokenizer(),
                tempDir
        );

        Namespace namespace = namespace(10L, "global");
        Skill targetSkill = skill(1L, 10L, "smart-agent", "Smart Agent", "Original title", 101L);
        Skill otherSkill = skill(2L, 10L, "helper-agent", "Helper Agent", "Other title", 202L);
        SkillVersion targetVersion = version(101L, "original");
        SkillVersion otherVersion = version(202L, "other");

        when(namespaceRepository.findById(10L)).thenReturn(Optional.of(namespace));
        when(skillVersionRepository.findById(101L)).thenReturn(Optional.of(targetVersion));
        when(skillVersionRepository.findById(202L)).thenReturn(Optional.of(otherVersion));
        when(skillRepository.findById(1L)).thenReturn(Optional.of(targetSkill));
        when(skillRepository.findById(2L)).thenReturn(Optional.of(otherSkill));

        rebuildService.rebuildBySkill(1L);
        rebuildService.rebuildBySkill(2L);

        assertThat(docCount()).isEqualTo(2);
        assertThat(storedField("1", "title")).isEqualTo("Smart Agent");
        assertThat(storedField("2", "title")).isEqualTo("Helper Agent");

        Skill updatedTargetSkill = skill(1L, 10L, "smart-agent", "Smart Agent v2", "Updated title", 303L);
        SkillVersion updatedTargetVersion = version(303L, "updated");

        when(skillRepository.findById(1L)).thenReturn(Optional.of(updatedTargetSkill));
        when(skillVersionRepository.findById(303L)).thenReturn(Optional.of(updatedTargetVersion));

        rebuildService.rebuildBySkill(1L);

        assertThat(docCount()).isEqualTo(2);
        assertThat(storedField("1", "title")).isEqualTo("Smart Agent v2");
        assertThat(storedField("2", "title")).isEqualTo("Helper Agent");

        Skill archivedTargetSkill = skill(1L, 10L, "smart-agent", "Smart Agent v2", "Updated title", 303L);
        archivedTargetSkill.setStatus(SkillStatus.ARCHIVED);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(archivedTargetSkill));

        rebuildService.rebuildBySkill(1L);

        assertThat(docCount()).isEqualTo(1);
        assertThat(hitCount("1")).isZero();
        assertThat(hitCount("2")).isEqualTo(1);
    }

    private Skill skill(Long id, Long namespaceId, String slug, String displayName, String summary, Long latestVersionId) {
        Skill skill = new Skill(namespaceId, slug, "owner-1", SkillVisibility.PUBLIC);
        setField(skill, "id", id);
        skill.setDisplayName(displayName);
        skill.setSummary(summary);
        skill.setLatestVersionId(latestVersionId);
        return skill;
    }

    private Namespace namespace(Long id, String slug) {
        Namespace namespace = new Namespace(slug, "Global", "system");
        setField(namespace, "id", id);
        return namespace;
    }

    private SkillVersion version(Long id, String keyword) {
        SkillVersion version = new SkillVersion(1L, "1.0.0", "owner-1");
        setField(version, "id", id);
        version.setParsedMetadataJson("""
                {
                  "frontmatter": {
                    "keywords": ["%s"]
                  }
                }
                """.formatted(keyword));
        return version;
    }

    private long docCount() throws Exception {
        try (Directory directory = FSDirectory.open(tempDir);
             IndexReader reader = DirectoryReader.open(directory)) {
            return reader.numDocs();
        }
    }

    private String storedField(String skillId, String fieldName) throws Exception {
        try (Directory directory = FSDirectory.open(tempDir);
             IndexReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            int docId = searcher.search(new TermQuery(new org.apache.lucene.index.Term("skillId", skillId)), 10)
                    .scoreDocs[0]
                    .doc;
            Document document = reader.storedFields().document(docId);
            return document.get(fieldName);
        }
    }

    private long hitCount(String skillId) throws Exception {
        try (Directory directory = FSDirectory.open(tempDir);
             IndexReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            return searcher.search(new TermQuery(new org.apache.lucene.index.Term("skillId", skillId)), 10)
                    .totalHits.value;
        }
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
