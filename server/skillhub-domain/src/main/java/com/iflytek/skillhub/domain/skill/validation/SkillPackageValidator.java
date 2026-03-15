package com.iflytek.skillhub.domain.skill.validation;

import com.iflytek.skillhub.domain.shared.exception.LocalizedDomainException;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SkillPackageValidator {

    private final SkillMetadataParser metadataParser;
    private final int maxFileCount;
    private final long maxSingleFileSize;
    private final long maxTotalPackageSize;
    private final Set<String> allowedExtensions;

    public SkillPackageValidator(SkillMetadataParser metadataParser) {
        this(
                metadataParser,
                SkillPackagePolicy.MAX_FILE_COUNT,
                SkillPackagePolicy.MAX_SINGLE_FILE_SIZE,
                SkillPackagePolicy.MAX_TOTAL_PACKAGE_SIZE,
                SkillPackagePolicy.ALLOWED_EXTENSIONS
        );
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
        Set<String> normalizedPaths = new HashSet<>();
        PackageEntry skillMd = null;

        for (PackageEntry entry : entries) {
            String normalizedPath;
            try {
                normalizedPath = SkillPackagePolicy.normalizeEntryPath(entry.path());
            } catch (IllegalArgumentException e) {
                errors.add(e.getMessage());
                continue;
            }

            if (!normalizedPaths.add(normalizedPath)) {
                errors.add("Duplicate package entry path: " + normalizedPath);
            }

            if (!hasAllowedExtension(normalizedPath)) {
                errors.add("Disallowed file extension: " + normalizedPath);
            }

            String contentMismatch = SkillPackagePolicy.validateContentMatchesExtension(normalizedPath, entry.content());
            if (contentMismatch != null) {
                errors.add(contentMismatch);
            }

            if (SkillPackagePolicy.SKILL_MD_PATH.equals(normalizedPath) && skillMd == null) {
                skillMd = entry;
            }
        }

        // 1. Check SKILL.md exists at root
        if (skillMd == null) {
            errors.add("Missing required file: SKILL.md at root");
            return ValidationResult.fail(errors);
        }

        // 2. Validate frontmatter
        try {
            String content = new String(skillMd.content());
            metadataParser.parse(content);
        } catch (LocalizedDomainException e) {
            String detail = e.messageArgs().length == 0
                    ? e.messageCode()
                    : e.messageCode() + " " + java.util.Arrays.toString(e.messageArgs());
            errors.add("Invalid SKILL.md frontmatter: " + detail);
        }

        // 3. Check file count
        if (entries.size() > maxFileCount) {
            errors.add("Too many files: " + entries.size() + " (max: " + maxFileCount + ")");
        }

        // 4. Check single file size
        for (PackageEntry entry : entries) {
            if (entry.size() > maxSingleFileSize) {
                errors.add("File too large: " + entry.path() + " (" + entry.size() + " bytes, max: " + maxSingleFileSize + ")");
            }
        }

        // 5. Check total package size
        long totalSize = entries.stream().mapToLong(PackageEntry::size).sum();
        if (totalSize > maxTotalPackageSize) {
            errors.add("Package too large: " + totalSize + " bytes (max: " + maxTotalPackageSize + ")");
        }

        return errors.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(errors);
    }

    private boolean hasAllowedExtension(String normalizedPath) {
        return allowedExtensions.stream().anyMatch(normalizedPath::endsWith);
    }
}
