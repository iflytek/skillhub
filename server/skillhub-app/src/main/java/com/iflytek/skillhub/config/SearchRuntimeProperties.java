package com.iflytek.skillhub.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Runtime search provider and local file index location settings.
 */
@Component
@ConfigurationProperties(prefix = "skillhub.search")
public class SearchRuntimeProperties {

    private String provider = "local-file-index";
    private boolean rebuildOnStartup = false;
    private boolean startupSyncEnabled = true;
    private final LocalFileIndex localFileIndex = new LocalFileIndex();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isRebuildOnStartup() {
        return rebuildOnStartup;
    }

    public void setRebuildOnStartup(boolean rebuildOnStartup) {
        this.rebuildOnStartup = rebuildOnStartup;
    }

    public boolean isStartupSyncEnabled() {
        return startupSyncEnabled;
    }

    public void setStartupSyncEnabled(boolean startupSyncEnabled) {
        this.startupSyncEnabled = startupSyncEnabled;
    }

    public LocalFileIndex getLocalFileIndex() {
        return localFileIndex;
    }

    public static class LocalFileIndex {

        private Path directory = Path.of(System.getProperty("user.home"), ".skillhub", "search-index");

        public Path getDirectory() {
            return directory;
        }

        public void setDirectory(Path directory) {
            this.directory = directory;
        }
    }
}
