package com.iflytek.skillhub.domain.skill.validation;

import com.iflytek.skillhub.domain.shared.exception.LocalizedDomainException;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SkillPackageValidator {

    private final SkillMetadataParser metadataParser;

    public SkillPackageValidator(SkillMetadataParser metadataParser) {
        this.metadataParser = metadataParser;
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

            if (!SkillPackagePolicy.hasAllowedExtension(normalizedPath)) {
                errors.add("Disallowed file extension: " + normalizedPath);
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
        if (entries.size() > SkillPackagePolicy.MAX_FILE_COUNT) {
            errors.add("Too many files: " + entries.size() + " (max: " + SkillPackagePolicy.MAX_FILE_COUNT + ")");
        }

        // 4. Check single file size
        for (PackageEntry entry : entries) {
            if (entry.size() > SkillPackagePolicy.MAX_SINGLE_FILE_SIZE) {
                errors.add("File too large: " + entry.path() + " (" + entry.size() + " bytes, max: " + SkillPackagePolicy.MAX_SINGLE_FILE_SIZE + ")");
            }
        }

        // 5. Check total package size
        long totalSize = entries.stream().mapToLong(PackageEntry::size).sum();
        if (totalSize > SkillPackagePolicy.MAX_TOTAL_PACKAGE_SIZE) {
            errors.add("Package too large: " + totalSize + " bytes (max: " + SkillPackagePolicy.MAX_TOTAL_PACKAGE_SIZE + ")");
        }

        return errors.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(errors);
    }
}
