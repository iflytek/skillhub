package com.iflytek.skillhub.domain.skill.validation;

import com.iflytek.skillhub.domain.shared.exception.LocalizedDomainException;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SkillPackageValidator {

    private static final String SKILL_MD_PATH = "SKILL.md";
    private static final Set<String> DEFAULT_ALLOWED_EXTENSIONS = Set.of(
        ".md", ".txt", ".json", ".yaml", ".yml",
        ".js", ".ts", ".py", ".sh",
        ".png", ".jpg", ".svg"
    );

    private final SkillMetadataParser metadataParser;
    private final int maxFileCount;
    private final long maxSingleFileSize;
    private final long maxTotalPackageSize;
    private final Set<String> allowedExtensions;

    public SkillPackageValidator(SkillMetadataParser metadataParser) {
        this(metadataParser, 100, 1024 * 1024, 10 * 1024 * 1024, DEFAULT_ALLOWED_EXTENSIONS);
    }

    public SkillPackageValidator(SkillMetadataParser metadataParser,
                                 int maxFileCount,
                                 long maxSingleFileSize,
                                 long maxTotalPackageSize,
                                 Set<String> allowedExtensions) {
        this.metadataParser = metadataParser;
        this.maxFileCount = maxFileCount;
        this.maxSingleFileSize = maxSingleFileSize;
        this.maxTotalPackageSize = maxTotalPackageSize;
        this.allowedExtensions = allowedExtensions.stream()
                .map(String::toLowerCase)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public ValidationResult validate(List<PackageEntry> entries) {
        List<String> errors = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();

        // 1. Check file count
        if (entries.size() > maxFileCount) {
            errors.add("Too many files: " + entries.size() + " (max: " + maxFileCount + ")");
        }

        // 2. Validate paths and duplicates
        for (PackageEntry entry : entries) {
            String normalizedPath = validateAndNormalizePath(entry.path(), errors);
            if (normalizedPath != null && !seenPaths.add(normalizedPath)) {
                errors.add("Duplicate file path: " + normalizedPath);
            }
        }

        // 3. Check SKILL.md exists at root
        PackageEntry skillMd = entries.stream()
            .filter(e -> e.path().equals(SKILL_MD_PATH))
            .findFirst()
            .orElse(null);

        if (skillMd == null) {
            errors.add("Missing required file: SKILL.md at root");
            return ValidationResult.fail(errors);
        }

        // 4. Validate frontmatter
        try {
            String content = new String(skillMd.content());
            metadataParser.parse(content);
        } catch (LocalizedDomainException e) {
            String detail = e.messageArgs().length == 0
                    ? e.messageCode()
                    : e.messageCode() + " " + java.util.Arrays.toString(e.messageArgs());
            errors.add("Invalid SKILL.md frontmatter: " + detail);
        }

        // 5. Check file extensions
        for (PackageEntry entry : entries) {
            String path = entry.path().toLowerCase();
            boolean hasAllowedExtension = allowedExtensions.stream().anyMatch(path::endsWith);
            if (!hasAllowedExtension) {
                errors.add("Disallowed file extension: " + path);
            }
        }

        // 6. Check single file size
        for (PackageEntry entry : entries) {
            if (entry.size() > maxSingleFileSize) {
                errors.add("File too large: " + entry.path() + " (" + entry.size() + " bytes, max: " + maxSingleFileSize + ")");
            }
        }

        // 7. Check total package size
        long totalSize = entries.stream().mapToLong(PackageEntry::size).sum();
        if (totalSize > maxTotalPackageSize) {
            errors.add("Package too large: " + totalSize + " bytes (max: " + maxTotalPackageSize + ")");
        }

        return errors.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(errors);
    }

    private String validateAndNormalizePath(String path, List<String> errors) {
        if (path == null || path.isBlank()) {
            errors.add("Package entry path must not be blank");
            return null;
        }
        if (path.contains("\\")) {
            errors.add("Package entry must use '/' separators: " + path);
            return null;
        }
        if (path.startsWith("/") || path.contains("//")) {
            errors.add("Unsafe file path: " + path);
            return null;
        }

        try {
            Path normalized = Path.of(path).normalize();
            String normalizedPath = normalized.toString().replace('\\', '/');
            if (normalized.isAbsolute()
                    || normalizedPath.isBlank()
                    || normalizedPath.equals(".")
                    || normalizedPath.equals("..")
                    || normalizedPath.startsWith("../")) {
                errors.add("Unsafe file path: " + path);
                return null;
            }
            return normalizedPath;
        } catch (InvalidPathException ex) {
            errors.add("Invalid file path: " + path);
            return null;
        }
    }
}
