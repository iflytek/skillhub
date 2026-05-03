package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                new SearchTextTokenizer()
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
