package com.iflytek.skillhub.domain.skill.validation;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

public final class SkillPackagePolicy {

    public static final int MAX_FILE_COUNT = 100;
    public static final long MAX_SINGLE_FILE_SIZE = 1024 * 1024; // 1MB
    public static final long MAX_TOTAL_PACKAGE_SIZE = 10 * 1024 * 1024; // 10MB
    public static final String SKILL_MD_PATH = "SKILL.md";
    public static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".md", ".txt", ".json", ".yaml", ".yml",
            ".js", ".ts", ".py", ".sh",
            ".png", ".jpg", ".svg"
    );

    private SkillPackagePolicy() {
    }

    public static String normalizeEntryPath(String rawPath) {
        if (rawPath == null) {
            throw new IllegalArgumentException("Package entry path is missing");
        }

        String sanitized = rawPath.replace('\\', '/').trim();
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Package entry path is empty");
        }
        if (sanitized.startsWith("/") || sanitized.startsWith("\\")) {
            throw new IllegalArgumentException("Package entry path must be relative: " + rawPath);
        }
        if (sanitized.contains(":")) {
            throw new IllegalArgumentException("Package entry path contains an invalid drive or scheme prefix: " + rawPath);
        }

        Path normalized = Paths.get(sanitized).normalize();
        String canonical = normalized.toString().replace('\\', '/');
        if (normalized.isAbsolute() || canonical.isBlank()) {
            throw new IllegalArgumentException("Package entry path is invalid: " + rawPath);
        }
        if (canonical.equals(".") || canonical.equals("..") || canonical.startsWith("../")) {
            throw new IllegalArgumentException("Package entry path escapes package root: " + rawPath);
        }
        if (!sanitized.equals(canonical)) {
            throw new IllegalArgumentException("Package entry path must be normalized: " + rawPath);
        }

        return canonical;
    }

    public static boolean hasAllowedExtension(String path) {
        return ALLOWED_EXTENSIONS.stream().anyMatch(path::endsWith);
    }
}
