package com.iflytek.skillhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "skillhub.publish")
public class SkillPublishProperties {

    private int maxFileCount = 100;
    private long maxSingleFileSize = 10 * 1024 * 1024;  // 10MB
    private long maxPackageSize = 100 * 1024 * 1024;
    private Set<String> allowedFileExtensions = new LinkedHashSet<>(Set.of(
            ".md", ".txt", ".json", ".yaml", ".yml", ".html", ".css", ".csv", ".pdf",
            ".toml", ".xml", ".ini", ".cfg", ".env",
            ".js", ".ts", ".py", ".sh", ".rb", ".go", ".rs", ".java", ".kt",
            ".lua", ".sql", ".r", ".bat", ".ps1", ".zsh", ".bash",
            ".png", ".jpg", ".jpeg", ".svg", ".gif", ".webp", ".ico"
    ));

    public int getMaxFileCount() {
        return maxFileCount;
    }

    public void setMaxFileCount(int maxFileCount) {
        this.maxFileCount = maxFileCount;
    }

    public long getMaxSingleFileSize() {
        return maxSingleFileSize;
    }

    public void setMaxSingleFileSize(long maxSingleFileSize) {
        this.maxSingleFileSize = maxSingleFileSize;
    }

    public long getMaxPackageSize() {
        return maxPackageSize;
    }

    public void setMaxPackageSize(long maxPackageSize) {
        this.maxPackageSize = maxPackageSize;
    }

    public Set<String> getAllowedFileExtensions() {
        return allowedFileExtensions;
    }

    public void setAllowedFileExtensions(Set<String> allowedFileExtensions) {
        this.allowedFileExtensions = new LinkedHashSet<>(allowedFileExtensions);
    }
}
