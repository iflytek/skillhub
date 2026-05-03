package com.iflytek.skillhub.search.localfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.label.LabelDefinitionRepository;
import com.iflytek.skillhub.domain.label.LabelTranslationRepository;
import com.iflytek.skillhub.domain.label.SkillLabelRepository;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.search.SearchIndexService;
import com.iflytek.skillhub.search.SearchTextTokenizer;
import com.iflytek.skillhub.search.SkillSearchDocument;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class LocalFileIndexRebuildServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void rebuildBySkillShouldRemoveMissingInactiveOrNamespaceLessSkill() {
        SkillRepository skillRepository = mock(SkillRepository.class);
        NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
        SkillVersionRepository skillVersionRepository = mock(SkillVersionRepository.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);

        Skill inactiveSkill = new Skill(10L, "inactive", "owner-1", SkillVisibility.PUBLIC);
        setField(inactiveSkill, "id", 2L);
        inactiveSkill.setStatus(SkillStatus.ARCHIVED);

        Skill missingNamespaceSkill = new Skill(20L, "missing-ns", "owner-1", SkillVisibility.PUBLIC);
        setField(missingNamespaceSkill, "id", 3L);

        when(skillRepository.findById(1L)).thenReturn(Optional.empty());
        when(skillRepository.findById(2L)).thenReturn(Optional.of(inactiveSkill));
        when(skillRepository.findById(3L)).thenReturn(Optional.of(missingNamespaceSkill));
        when(namespaceRepository.findById(20L)).thenReturn(Optional.empty());

        LocalFileIndexRebuildService service = new LocalFileIndexRebuildService(
                skillRepository,
                namespaceRepository,
                skillVersionRepository,
                mock(LabelDefinitionRepository.class),
                mock(LabelTranslationRepository.class),
                mock(SkillLabelRepository.class),
                searchIndexService,
                new SearchTextTokenizer(),
                tempDir
        );

        service.rebuildBySkill(1L);
        service.rebuildBySkill(2L);
        service.rebuildBySkill(3L);

        verify(searchIndexService).remove(1L);
        verify(searchIndexService).remove(2L);
        verify(searchIndexService).remove(3L);
        verify(searchIndexService, never()).index(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rebuildByNamespaceShouldIndexActiveAndRemoveStaleSkillIds() {
        SkillRepository skillRepository = mock(SkillRepository.class);
        NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
        SkillVersionRepository skillVersionRepository = mock(SkillVersionRepository.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);

        LocalFileIndexService indexService = new LocalFileIndexService(tempDir);
        indexService.index(document(99L, 10L, "stale"));

        Skill activeSkill = new Skill(10L, "fresh", "owner-1", SkillVisibility.PUBLIC);
        setField(activeSkill, "id", 1L);
        activeSkill.setSummary("indexed");

        Namespace namespace = new Namespace("team-ai", "Team AI", "owner-1");

        when(skillRepository.findByNamespaceIdAndStatus(10L, SkillStatus.ACTIVE)).thenReturn(List.of(activeSkill));
        when(namespaceRepository.findById(10L)).thenReturn(Optional.of(namespace));

        LocalFileIndexRebuildService service = new LocalFileIndexRebuildService(
                skillRepository,
                namespaceRepository,
                skillVersionRepository,
                mock(LabelDefinitionRepository.class),
                mock(LabelTranslationRepository.class),
                mock(SkillLabelRepository.class),
                searchIndexService,
                new SearchTextTokenizer(),
                tempDir
        );

        service.rebuildByNamespace(10L);

        verify(searchIndexService).remove(99L);
        ArgumentCaptor<SkillSearchDocument> documentCaptor = ArgumentCaptor.forClass(SkillSearchDocument.class);
        verify(searchIndexService).index(documentCaptor.capture());
        assertThat(documentCaptor.getValue().skillId()).isEqualTo(1L);
    }

    @Test
    void rebuildAllShouldResetExistingIndexDirectory() throws Exception {
        SkillRepository skillRepository = mock(SkillRepository.class);
        NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
        SkillVersionRepository skillVersionRepository = mock(SkillVersionRepository.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);

        Files.createDirectories(tempDir.resolve("nested"));
        Files.writeString(tempDir.resolve("nested/stale.txt"), "stale");

        when(skillRepository.findAll()).thenReturn(List.of());

        LocalFileIndexRebuildService service = new LocalFileIndexRebuildService(
                skillRepository,
                namespaceRepository,
                skillVersionRepository,
                mock(LabelDefinitionRepository.class),
                mock(LabelTranslationRepository.class),
                mock(SkillLabelRepository.class),
                searchIndexService,
                new SearchTextTokenizer(),
                tempDir
        );

        service.rebuildAll();

        assertThat(Files.exists(tempDir.resolve("nested/stale.txt"))).isFalse();
        verify(searchIndexService).batchIndex(List.of());
    }

    @Test
    void rebuildByNamespaceShouldTreatMissingOrUninitializedIndexAsEmpty() throws Exception {
        SkillRepository skillRepository = mock(SkillRepository.class);
        NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
        SkillVersionRepository skillVersionRepository = mock(SkillVersionRepository.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);

        Skill activeSkill = new Skill(10L, "fresh", "owner-1", SkillVisibility.PUBLIC);
        setField(activeSkill, "id", 1L);
        activeSkill.setSummary("indexed");
        Namespace namespace = new Namespace("team-ai", "Team AI", "owner-1");

        when(skillRepository.findByNamespaceIdAndStatus(10L, SkillStatus.ACTIVE)).thenReturn(List.of(activeSkill));
        when(namespaceRepository.findById(10L)).thenReturn(Optional.of(namespace));

        LocalFileIndexRebuildService missingIndexService = new LocalFileIndexRebuildService(
                skillRepository,
                namespaceRepository,
                skillVersionRepository,
                mock(LabelDefinitionRepository.class),
                mock(LabelTranslationRepository.class),
                mock(SkillLabelRepository.class),
                searchIndexService,
                new SearchTextTokenizer(),
                tempDir.resolve("missing-index")
        );

        missingIndexService.rebuildByNamespace(10L);

        Files.createDirectories(tempDir.resolve("empty-index"));
        LocalFileIndexRebuildService uninitializedIndexService = new LocalFileIndexRebuildService(
                skillRepository,
                namespaceRepository,
                skillVersionRepository,
                mock(LabelDefinitionRepository.class),
                mock(LabelTranslationRepository.class),
                mock(SkillLabelRepository.class),
                searchIndexService,
                new SearchTextTokenizer(),
                tempDir.resolve("empty-index")
        );

        uninitializedIndexService.rebuildByNamespace(10L);

        verify(searchIndexService, never()).remove(99L);
        verify(searchIndexService, org.mockito.Mockito.times(2)).index(org.mockito.ArgumentMatchers.any(SkillSearchDocument.class));
    }

    @Test
    void rebuildByNamespaceShouldWrapIndexInspectionFailure() {
        SkillRepository skillRepository = mock(SkillRepository.class);
        NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
        SkillVersionRepository skillVersionRepository = mock(SkillVersionRepository.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);

        Skill activeSkill = new Skill(10L, "fresh", "owner-1", SkillVisibility.PUBLIC);
        setField(activeSkill, "id", 1L);
        activeSkill.setSummary("indexed");
        Namespace namespace = new Namespace("team-ai", "Team AI", "owner-1");

        when(skillRepository.findByNamespaceIdAndStatus(10L, SkillStatus.ACTIVE)).thenReturn(List.of(activeSkill));
        when(namespaceRepository.findById(10L)).thenReturn(Optional.of(namespace));

        LocalFileIndexRebuildService service = new LocalFileIndexRebuildService(
                skillRepository,
                namespaceRepository,
                skillVersionRepository,
                mock(LabelDefinitionRepository.class),
                mock(LabelTranslationRepository.class),
                mock(SkillLabelRepository.class),
                searchIndexService,
                new SearchTextTokenizer(),
                tempDir
        ) {
            @Override
            protected org.apache.lucene.store.Directory openDirectory(Path directory) throws IOException {
                throw new IOException("boom");
            }
        };

        assertThatThrownBy(() -> service.rebuildByNamespace(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to inspect local file index");
    }

    @Test
    void rebuildAllShouldReturnWhenDirectoryMissingAndWrapResetFailures() {
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        SkillRepository missingDirRepository = mock(SkillRepository.class);
        when(missingDirRepository.findAll()).thenReturn(List.of());

        LocalFileIndexRebuildService missingDirService = new LocalFileIndexRebuildService(
                missingDirRepository,
                mock(NamespaceRepository.class),
                mock(SkillVersionRepository.class),
                mock(LabelDefinitionRepository.class),
                mock(LabelTranslationRepository.class),
                mock(SkillLabelRepository.class),
                searchIndexService,
                new SearchTextTokenizer(),
                tempDir.resolve("missing-reset-dir")
        );
        missingDirService.rebuildAll();

        SkillRepository walkFailureRepository = mock(SkillRepository.class);
        when(walkFailureRepository.findAll()).thenReturn(List.of());
        LocalFileIndexRebuildService walkFailureService = new LocalFileIndexRebuildService(
                walkFailureRepository,
                mock(NamespaceRepository.class),
                mock(SkillVersionRepository.class),
                mock(LabelDefinitionRepository.class),
                mock(LabelTranslationRepository.class),
                mock(SkillLabelRepository.class),
                searchIndexService,
                new SearchTextTokenizer(),
                tempDir
        ) {
            @Override
            protected Stream<Path> walkIndexDirectory(Path directory) throws IOException {
                throw new IOException("walk-failed");
            }
        };
        assertThatThrownBy(walkFailureService::rebuildAll)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to reset local file index");

        SkillRepository deleteFailureRepository = mock(SkillRepository.class);
        when(deleteFailureRepository.findAll()).thenReturn(List.of());
        LocalFileIndexRebuildService deleteFailureService = new LocalFileIndexRebuildService(
                deleteFailureRepository,
                mock(NamespaceRepository.class),
                mock(SkillVersionRepository.class),
                mock(LabelDefinitionRepository.class),
                mock(LabelTranslationRepository.class),
                mock(SkillLabelRepository.class),
                searchIndexService,
                new SearchTextTokenizer(),
                tempDir
        ) {
            @Override
            protected Stream<Path> walkIndexDirectory(Path directory) {
                return Stream.of(directory.resolve("child"), directory);
            }

            @Override
            protected void deletePath(Path path) throws IOException {
                if (path.endsWith("child")) {
                    throw new IOException("delete-failed");
                }
            }
        };
        assertThatThrownBy(deleteFailureService::rebuildAll)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to reset local file index");
    }

    private SkillSearchDocument document(Long skillId, Long namespaceId, String title) {
        return new SkillSearchDocument(
                skillId,
                namespaceId,
                "team-ai",
                "owner-" + skillId,
                title,
                "summary",
                "keywords",
                "search text",
                null,
                "PUBLIC",
                "ACTIVE",
                List.of("official"),
                1L,
                1.0D,
                1L,
                "ACTIVE",
                false
        );
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
