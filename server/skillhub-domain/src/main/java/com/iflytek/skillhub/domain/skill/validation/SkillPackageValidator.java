package com.iflytek.skillhub.domain.skill.validation;

import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SkillPackageValidator {

    private static final int MAX_FILE_COUNT = 100;
    private static final long MAX_SINGLE_FILE_SIZE = 1024 * 1024; // 1MB
    private static final long MAX_TOTAL_PACKAGE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final String SKILL_MD_PATH = "SKILL.md";
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        ".md", ".txt", ".json", ".yaml", ".yml",
        ".js", ".ts", ".py", ".sh",
        ".png", ".jpg", ".svg"
    );

    private final SkillMetadataParser metadataParser;

    public SkillPackageValidator(SkillMetadataParser metadataParser) {
        this.metadataParser = metadataParser;
    }

    public ValidationResult validate(List<PackageEntry> entries) {
        List<String> errors = new ArrayList<>();

        // 1. Check SKILL.md exists at root
        PackageEntry skillMd = entries.stream()
            .filter(e -> e.path().equals(SKILL_MD_PATH))
            .findFirst()
            .orElse(null);

        if (skillMd == null) {
            errors.add("Missing required file: SKILL.md at root");
            return ValidationResult.fail(errors);
        }

        // 2. Validate frontmatter
        try {
            String content = new String(skillMd.content());
            metadataParser.parse(content);
        } catch (IllegalArgumentException e) {
            errors.add("Invalid SKILL.md frontmatter: " + e.getMessage());
        }

        // 3. Check file count
        if (entries.size() > MAX_FILE_COUNT) {
            errors.add("Too many files: " + entries.size() + " (max: " + MAX_FILE_COUNT + ")");
        }

        // 4. Check file extensions
        for (PackageEntry entry : entries) {
            String path = entry.path();
            boolean hasAllowedExtension = ALLOWED_EXTENSIONS.stream()
                .anyMatch(path::endsWith);
            if (!hasAllowedExtension) {
                errors.add("Disallowed file extension: " + path);
            }
        }

        // 5. Check single file size
        for (PackageEntry entry : entries) {
            if (entry.size() > MAX_SINGLE_FILE_SIZE) {
                errors.add("File too large: " + entry.path() + " (" + entry.size() + " bytes, max: " + MAX_SINGLE_FILE_SIZE + ")");
            }
        }

        // 6. Check total package size
        long totalSize = entries.stream().mapToLong(PackageEntry::size).sum();
        if (totalSize > MAX_TOTAL_PACKAGE_SIZE) {
            errors.add("Package too large: " + totalSize + " bytes (max: " + MAX_TOTAL_PACKAGE_SIZE + ")");
        }

        return errors.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(errors);
    }
}
