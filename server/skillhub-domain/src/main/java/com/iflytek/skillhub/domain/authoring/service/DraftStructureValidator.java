package com.iflytek.skillhub.domain.authoring.service;

import com.iflytek.skillhub.domain.authoring.FixSuggestion;
import com.iflytek.skillhub.domain.authoring.FilePatch;
import com.iflytek.skillhub.domain.authoring.runtime.TaskResult;
import com.iflytek.skillhub.domain.authoring.validation.FindingDraft;
import com.iflytek.skillhub.domain.authoring.validation.ValidationLayer;
import com.iflytek.skillhub.domain.shared.exception.LocalizedDomainException;
import com.iflytek.skillhub.domain.skill.metadata.ComplianceMetadataService;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * The STRUCTURE validation layer: package layout, SKILL.md frontmatter, file limits,
 * and content/extension consistency. Mirrors the checks of the publish-time
 * {@code SkillPackageValidator} rule by rule, but reports structured findings with
 * machine-applicable fix suggestions instead of opaque error strings.
 *
 * <p>Package limits and path rules come from the shared {@link SkillPackagePolicy},
 * so draft-time and publish-time rules cannot drift.
 */
@Service
public class DraftStructureValidator {

    private final SkillMetadataParser metadataParser;
    private final ComplianceMetadataService complianceMetadataService;
    private final SkillScaffoldGenerator scaffoldGenerator;

    public DraftStructureValidator(SkillMetadataParser metadataParser,
                                   SkillScaffoldGenerator scaffoldGenerator) {
        this.metadataParser = metadataParser;
        this.complianceMetadataService = new ComplianceMetadataService();
        this.scaffoldGenerator = scaffoldGenerator;
    }

    public record StructureReport(List<FindingDraft> findings, boolean skillMdPresent) {

        public boolean hasErrors() {
            return findings.stream().anyMatch(finding -> finding.severity()
                    == com.iflytek.skillhub.domain.authoring.validation.FindingSeverity.ERROR);
        }
    }

    /**
     * @param draftName   draft name, used to build fix suggestions (e.g. missing SKILL.md)
     * @param requirement original requirement text, reused in scaffold suggestions
     */
    public StructureReport validate(String draftName, String requirement, List<PackageEntry> entries) {
        List<FindingDraft> findings = new ArrayList<>();
        Set<String> normalizedPaths = new HashSet<>();
        PackageEntry skillMd = null;
        long totalSize = 0;

        for (PackageEntry entry : entries) {
            String normalizedPath;
            try {
                normalizedPath = SkillPackagePolicy.normalizeEntryPath(entry.path());
            } catch (IllegalArgumentException exception) {
                findings.add(FindingDraft.error(ValidationLayer.STRUCTURE, "PATH_INVALID",
                        entry.path(), exception.getMessage(), null));
                continue;
            }
            normalizedPath = SkillPackagePolicy.canonicalizeSkillMdPath(normalizedPath);

            if (!normalizedPaths.add(normalizedPath)) {
                findings.add(FindingDraft.error(ValidationLayer.STRUCTURE, "PATH_DUPLICATE",
                        normalizedPath, "Duplicate file path: " + normalizedPath, null));
            }
            if (!SkillPackagePolicy.hasAllowedExtension(normalizedPath)) {
                findings.add(FindingDraft.warning(ValidationLayer.STRUCTURE, "EXTENSION_DISALLOWED",
                        normalizedPath, "Disallowed file extension: " + normalizedPath));
            }
            String contentMismatch = SkillPackagePolicy.validateContentMatchesExtension(
                    normalizedPath, entry.content());
            if (contentMismatch != null) {
                findings.add(FindingDraft.error(ValidationLayer.STRUCTURE, "CONTENT_MISMATCH",
                        normalizedPath, contentMismatch, null));
            }
            if (entry.size() > SkillPackagePolicy.MAX_SINGLE_FILE_SIZE) {
                findings.add(FindingDraft.error(ValidationLayer.STRUCTURE, "FILE_TOO_LARGE",
                        normalizedPath, "File too large: " + normalizedPath + " ("
                                + entry.size() + " bytes, max " + SkillPackagePolicy.MAX_SINGLE_FILE_SIZE + ")",
                        null));
            }
            if (SkillPackagePolicy.SKILL_MD_PATH.equals(normalizedPath) && skillMd == null) {
                skillMd = entry;
            }
            totalSize += entry.size();
        }

        if (entries.size() > SkillPackagePolicy.MAX_FILE_COUNT) {
            findings.add(FindingDraft.error(ValidationLayer.STRUCTURE, "FILE_COUNT_EXCEEDED", null,
                    "Too many files: " + entries.size() + " (max " + SkillPackagePolicy.MAX_FILE_COUNT + ")",
                    null));
        }
        if (totalSize > SkillPackagePolicy.MAX_TOTAL_PACKAGE_SIZE) {
            findings.add(FindingDraft.error(ValidationLayer.STRUCTURE, "PACKAGE_TOO_LARGE", null,
                    "Package too large: " + totalSize + " bytes (max "
                            + SkillPackagePolicy.MAX_TOTAL_PACKAGE_SIZE + ")", null));
        }

        if (skillMd == null) {
            String scaffold = scaffoldGenerator.generateSkillMd(draftName, requirement);
            findings.add(FindingDraft.error(ValidationLayer.STRUCTURE, "SKILL_MD_MISSING", null,
                    "Missing required file: SKILL.md at root",
                    new FixSuggestion("Create an initial SKILL.md scaffold",
                            List.of(new FilePatch(SkillPackagePolicy.SKILL_MD_PATH, null, null, scaffold)))));
            return new StructureReport(findings, false);
        }

        String skillMdContent = new String(skillMd.content(), StandardCharsets.UTF_8);
        try {
            var metadata = metadataParser.parse(skillMdContent);
            findings.addAll(complianceFindings(metadata.frontmatter(), entries));
        } catch (LocalizedDomainException exception) {
            findings.add(frontmatterFinding(exception, skillMdContent, draftName));
        }

        return new StructureReport(findings, true);
    }

    private List<FindingDraft> complianceFindings(Map<String, Object> frontmatter,
                                                  List<PackageEntry> entries) {
        List<FindingDraft> findings = new ArrayList<>();
        for (String problem : complianceMetadataService.validate(frontmatter, entries)) {
            findings.add(FindingDraft.error(ValidationLayer.STRUCTURE, "COMPLIANCE_INVALID",
                    SkillPackagePolicy.SKILL_MD_PATH, problem, null));
        }
        return findings;
    }

    private FindingDraft frontmatterFinding(LocalizedDomainException exception,
                                            String skillMdContent, String draftName) {
        return switch (exception.messageCode()) {
            case "error.skill.metadata.requiredField.missing" -> {
                String field = String.valueOf(exception.messageArgs()[0]);
                String replacement = insertFrontmatterField(skillMdContent, field,
                        suggestionValue(field, draftName));
                yield FindingDraft.error(ValidationLayer.STRUCTURE, "FRONTMATTER_FIELD_MISSING",
                        SkillPackagePolicy.SKILL_MD_PATH,
                        "SKILL.md frontmatter is missing required field: " + field,
                        new FixSuggestion("Add required field '" + field + "'",
                                List.of(new FilePatch(SkillPackagePolicy.SKILL_MD_PATH,
                                        TaskResult.sha256Hex(skillMdContent),
                                        skillMdContent, replacement))));
            }
            case "error.skill.metadata.frontmatter.missingStart" -> FindingDraft.error(
                    ValidationLayer.STRUCTURE, "FRONTMATTER_MISSING_START",
                    SkillPackagePolicy.SKILL_MD_PATH,
                    "SKILL.md must start with a '---' frontmatter block", null);
            case "error.skill.metadata.frontmatter.missingEnd" -> FindingDraft.error(
                    ValidationLayer.STRUCTURE, "FRONTMATTER_MISSING_END",
                    SkillPackagePolicy.SKILL_MD_PATH,
                    "SKILL.md frontmatter is missing the closing '---' marker", null);
            case "error.skill.metadata.frontmatter.missingContent" -> FindingDraft.error(
                    ValidationLayer.STRUCTURE, "FRONTMATTER_EMPTY",
                    SkillPackagePolicy.SKILL_MD_PATH, "SKILL.md frontmatter is empty", null);
            case "error.skill.metadata.yaml.notMap" -> FindingDraft.error(
                    ValidationLayer.STRUCTURE, "FRONTMATTER_NOT_MAP",
                    SkillPackagePolicy.SKILL_MD_PATH,
                    "SKILL.md frontmatter must be a YAML object", null);
            case "error.skill.metadata.yaml.invalid" -> FindingDraft.error(
                    ValidationLayer.STRUCTURE, "FRONTMATTER_YAML_INVALID",
                    SkillPackagePolicy.SKILL_MD_PATH,
                    "SKILL.md frontmatter has invalid YAML: "
                            + (exception.messageArgs().length > 0 ? exception.messageArgs()[0] : ""),
                    null);
            default -> FindingDraft.error(ValidationLayer.STRUCTURE, "FRONTMATTER_INVALID",
                    SkillPackagePolicy.SKILL_MD_PATH, exception.messageCode(), null);
        };
    }

    private String suggestionValue(String field, String draftName) {
        return switch (field) {
            case "name" -> draftName == null ? "my-skill" : draftName;
            case "description" -> "TODO: describe what this skill does";
            default -> "TODO";
        };
    }

    /** Inserts {@code field: value} as the first line inside the frontmatter block. */
    private String insertFrontmatterField(String content, String field, String value) {
        String[] lines = content.split("\n", -1);
        if (lines.length == 0 || !lines[0].trim().equals("---")) {
            return "---\n" + field + ": " + value + "\n---\n" + content;
        }
        StringBuilder rebuilt = new StringBuilder();
        rebuilt.append(lines[0]).append('\n');
        rebuilt.append(field).append(": ").append(value).append('\n');
        for (int i = 1; i < lines.length; i++) {
            rebuilt.append(lines[i]);
            if (i < lines.length - 1) {
                rebuilt.append('\n');
            }
        }
        return rebuilt.toString();
    }
}
