package com.iflytek.skillhub.bootstrap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.iflytek.skillhub.config.SearchRuntimeProperties;
import com.iflytek.skillhub.search.SearchRebuildService;
import com.iflytek.skillhub.search.SkillSearchDocument;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;

class LocalFileIndexStartupSynchronizerTest {

    @TempDir
    Path tempDir;

    @Test
    void missingLocalIndexShouldTriggerInitialRebuild() throws Exception {
        SearchRuntimeProperties properties = new SearchRuntimeProperties();
        properties.setProvider("local-file-index");
        properties.getLocalFileIndex().setDirectory(tempDir.resolve("missing-index"));
        SearchRebuildService rebuildService = mock(SearchRebuildService.class);

        LocalFileIndexStartupSynchronizer synchronizer =
                new LocalFileIndexStartupSynchronizer(properties, rebuildService);

        synchronizer.run(new DefaultApplicationArguments(new String[0]));

        verify(rebuildService).rebuildAll();
    }

    @Test
    void rebuildOnStartupShouldTriggerRebuildEvenWhenIndexExists() throws Exception {
        SearchRuntimeProperties properties = new SearchRuntimeProperties();
        properties.setProvider("local-file-index");
        properties.setRebuildOnStartup(true);
        properties.getLocalFileIndex().setDirectory(tempDir);
        Files.createDirectories(tempDir);
        // Create a minimal Lucene index so DirectoryReader.indexExists(directory) returns true.
        new com.iflytek.skillhub.search.localfile.LocalFileIndexService(tempDir).index(new SkillSearchDocument(
                1L,
                1L,
                "global",
                "owner",
                "Seed Title",
                "Seed summary",
                "seed",
                "seed search text",
                "",
                "PUBLIC",
                "ACTIVE",
                java.util.List.of(),
                0L,
                0D,
                0L,
                "ACTIVE",
                false
        ));

        SearchRebuildService rebuildService = mock(SearchRebuildService.class);
        LocalFileIndexStartupSynchronizer synchronizer =
                new LocalFileIndexStartupSynchronizer(properties, rebuildService);

        synchronizer.run(new DefaultApplicationArguments(new String[0]));

        verify(rebuildService).rebuildAll();
    }

    @Test
    void nonLocalFileIndexProviderShouldSkipStartupSync() throws Exception {
        SearchRuntimeProperties properties = new SearchRuntimeProperties();
        properties.setProvider("mysql-like");
        properties.getLocalFileIndex().setDirectory(tempDir);
        SearchRebuildService rebuildService = mock(SearchRebuildService.class);

        LocalFileIndexStartupSynchronizer synchronizer =
                new LocalFileIndexStartupSynchronizer(properties, rebuildService);

        synchronizer.run(new DefaultApplicationArguments(new String[0]));

        verify(rebuildService, never()).rebuildAll();
    }

    @Test
    void corruptedLocalIndexShouldTriggerRecoveryRebuild() throws Exception {
        SearchRuntimeProperties properties = new SearchRuntimeProperties();
        properties.setProvider("local-file-index");
        properties.getLocalFileIndex().setDirectory(tempDir);
        SearchRebuildService rebuildService = mock(SearchRebuildService.class);

        LocalFileIndexStartupSynchronizer synchronizer =
                new LocalFileIndexStartupSynchronizer(properties, rebuildService) {
                    @Override
                    protected IndexInspection inspectIndex(Path indexDirectory) {
                        return IndexInspection.CORRUPTED;
                    }
                };

        synchronizer.run(new DefaultApplicationArguments(new String[0]));

        verify(rebuildService).rebuildAll();
    }
}
