package com.iflytek.skillhub.search.localfile;

import com.iflytek.skillhub.domain.label.LabelDefinitionRepository;
import com.iflytek.skillhub.domain.label.LabelTranslationRepository;
import com.iflytek.skillhub.domain.label.SkillLabelRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.search.AbstractJpaSearchRebuildService;
import com.iflytek.skillhub.search.SearchIndexService;
import com.iflytek.skillhub.search.SearchTextTokenizer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Rebuilds Lucene documents from canonical data for the local-file-index provider.
 */
@Service
@ConditionalOnProperty(prefix = "skillhub.search", name = "provider", havingValue = "local-file-index")
public class LocalFileIndexRebuildService extends AbstractJpaSearchRebuildService {
    private final Path indexDirectory;

    public LocalFileIndexRebuildService(
            SkillRepository skillRepository,
            NamespaceRepository namespaceRepository,
            SkillVersionRepository skillVersionRepository,
            LabelDefinitionRepository labelDefinitionRepository,
            LabelTranslationRepository labelTranslationRepository,
            SkillLabelRepository skillLabelRepository,
            SearchIndexService searchIndexService,
            SearchTextTokenizer searchTextTokenizer,
            @Value("${skillhub.search.local-file-index.directory}") Path indexDirectory) {
        super(
                skillRepository,
                namespaceRepository,
                skillVersionRepository,
                labelDefinitionRepository,
                labelTranslationRepository,
                skillLabelRepository,
                searchIndexService,
                searchTextTokenizer
        );
        this.indexDirectory = indexDirectory;
    }

    @Override
    public void rebuildAll() {
        resetIndexDirectory();
        super.rebuildAll();
    }

    @Override
    public void rebuildByNamespace(Long namespaceId) {
        List<Skill> skills = findActiveSkillsByNamespace(namespaceId);
        Set<Long> activeSkillIds = new HashSet<>();
        for (Skill skill : skills) {
            activeSkillIds.add(skill.getId());
            rebuildSkillDocument(skill.getId(), Optional.of(skill));
        }
        for (Long indexedSkillId : indexedSkillIdsForNamespace(namespaceId)) {
            if (!activeSkillIds.contains(indexedSkillId)) {
                removeDocument(indexedSkillId);
            }
        }
    }

    @Override
    public void rebuildBySkill(Long skillId) {
        rebuildSkillDocument(skillId, findSkillById(skillId));
    }

    private void rebuildSkillDocument(Long skillId, Optional<Skill> skillOpt) {
        if (skillOpt.isEmpty() || skillOpt.get().getStatus() != SkillStatus.ACTIVE) {
            removeDocument(skillId);
            return;
        }

        Optional<com.iflytek.skillhub.search.SkillSearchDocument> documentOpt = toDocument(skillOpt.get());
        if (documentOpt.isPresent()) {
            indexDocument(documentOpt.get());
            return;
        }
        removeDocument(skillId);
    }

    private Set<Long> indexedSkillIdsForNamespace(Long namespaceId) {
        if (Files.notExists(indexDirectory)) {
            return Set.of();
        }
        try (Directory directory = FSDirectory.open(indexDirectory)) {
            if (!DirectoryReader.indexExists(directory)) {
                return Set.of();
            }
            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                Set<Long> skillIds = new HashSet<>();
                for (var scoreDoc : searcher.search(LongPoint.newExactQuery("namespaceId", namespaceId), reader.numDocs())
                        .scoreDocs) {
                    Document document = reader.storedFields().document(scoreDoc.doc);
                    String skillId = document.get("skillId");
                    if (skillId != null) {
                        skillIds.add(Long.valueOf(skillId));
                    }
                }
                return skillIds;
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to inspect local file index at " + indexDirectory, ex);
        }
    }

    private void resetIndexDirectory() {
        if (Files.notExists(indexDirectory)) {
            return;
        }
        try (var paths = Files.walk(indexDirectory)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ex) {
                            throw new IllegalStateException(
                                    "Failed to reset local file index at " + indexDirectory,
                                    ex
                            );
                        }
                    });
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to reset local file index at " + indexDirectory, ex);
        }
    }
}
