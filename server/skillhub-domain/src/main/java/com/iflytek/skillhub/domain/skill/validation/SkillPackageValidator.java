package com.iflytek.skillhub.domain.skill.validation;

import com.iflytek.skillhub.domain.shared.exception.LocalizedDomainException;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SkillPackageValidator {
    private static final Pattern YAML_LINE_COLUMN = Pattern.compile("line\\s+(\\d+),\\s+column\\s+(\\d+)");

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
            errors.add("Invalid SKILL.md frontmatter: " + formatMetadataError(e));
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

    private String formatMetadataError(LocalizedDomainException exception) {
        return switch (exception.messageCode()) {
            case "error.skill.metadata.requiredField.missing" ->
                    "missing required field \"" + exception.messageArgs()[0] + "\"";
            case "error.skill.metadata.frontmatter.missingStart" ->
                    "missing opening --- marker";
            case "error.skill.metadata.frontmatter.missingEnd" ->
                    "missing closing --- marker";
            case "error.skill.metadata.frontmatter.missingContent" ->
                    "frontmatter is empty";
            case "error.skill.metadata.yaml.notMap" ->
                    "frontmatter must be a YAML object";
            case "error.skill.metadata.yaml.invalid" ->
                    formatYamlSyntaxError(exception.messageArgs());
            default -> {
                if (exception.messageArgs().length == 0) {
                    yield exception.messageCode();
                }
                yield exception.messageCode() + " " + java.util.Arrays.toString(exception.messageArgs());
            }
        };
    }

    private String formatYamlSyntaxError(Object[] args) {
        String raw = args.length > 0 && args[0] != null ? args[0].toString() : "";
        Matcher matcher = YAML_LINE_COLUMN.matcher(raw);
        if (matcher.find()) {
            return "invalid YAML near line " + matcher.group(1)
                    + ", column " + matcher.group(2)
                    + ". If a value contains a colon, wrap it in quotes.";
        }
        return "invalid YAML syntax";
    }
}
