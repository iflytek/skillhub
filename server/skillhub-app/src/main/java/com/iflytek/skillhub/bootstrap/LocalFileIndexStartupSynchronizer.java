package com.iflytek.skillhub.bootstrap;

import com.iflytek.skillhub.config.SearchRuntimeProperties;
import com.iflytek.skillhub.search.SearchRebuildService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Ensures the Lucene local-file-index has an initial data sync when it becomes
 * the active search provider.
 */
@Component
@Order(5)
public class LocalFileIndexStartupSynchronizer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalFileIndexStartupSynchronizer.class);
    private static final String LOCAL_FILE_INDEX_PROVIDER = "local-file-index";

    private final SearchRuntimeProperties searchRuntimeProperties;
    private final SearchRebuildService searchRebuildService;

    public LocalFileIndexStartupSynchronizer(SearchRuntimeProperties searchRuntimeProperties,
                                             SearchRebuildService searchRebuildService) {
        this.searchRuntimeProperties = searchRuntimeProperties;
        this.searchRebuildService = searchRebuildService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!LOCAL_FILE_INDEX_PROVIDER.equals(searchRuntimeProperties.getProvider())) {
            return;
        }

        Path indexDirectory = searchRuntimeProperties.getLocalFileIndex().getDirectory();
        IndexInspection inspection = inspectIndex(indexDirectory);

        if (inspection != IndexInspection.READY) {
            log.warn(
                    "local-file-index provider is active but the Lucene index at {} is {}. Triggering initial rebuild.",
                    indexDirectory,
                    inspection.logLabel
            );
            searchRebuildService.rebuildAll();
            return;
        }

        if (searchRuntimeProperties.isRebuildOnStartup()) {
            log.warn(
                    "local-file-index startup rebuild is enabled. Rebuilding Lucene index at {} before serving search traffic.",
                    indexDirectory
            );
            searchRebuildService.rebuildAll();
        }
    }

    protected IndexInspection inspectIndex(Path indexDirectory) {
        if (indexDirectory == null || Files.notExists(indexDirectory)) {
            return IndexInspection.MISSING;
        }
        try (Directory directory = FSDirectory.open(indexDirectory)) {
            if (!DirectoryReader.indexExists(directory)) {
                return IndexInspection.UNINITIALIZED;
            }
            try (IndexReader ignored = DirectoryReader.open(directory)) {
                return IndexInspection.READY;
            }
        } catch (IOException exception) {
            log.warn("Failed to inspect local-file-index directory {}. Treating it as corrupted.", indexDirectory, exception);
            return IndexInspection.CORRUPTED;
        }
    }

    protected enum IndexInspection {
        READY("ready"),
        MISSING("missing"),
        UNINITIALIZED("uninitialized"),
        CORRUPTED("corrupted");

        private final String logLabel;

        IndexInspection(String logLabel) {
            this.logLabel = logLabel;
        }
    }
}
