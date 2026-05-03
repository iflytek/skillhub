package com.iflytek.skillhub.search.localfile;

import com.iflytek.skillhub.domain.label.LabelDefinitionRepository;
import com.iflytek.skillhub.domain.label.LabelTranslationRepository;
import com.iflytek.skillhub.domain.label.SkillLabelRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.search.AbstractJpaSearchRebuildService;
import com.iflytek.skillhub.search.SearchIndexService;
import com.iflytek.skillhub.search.SearchTextTokenizer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
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
