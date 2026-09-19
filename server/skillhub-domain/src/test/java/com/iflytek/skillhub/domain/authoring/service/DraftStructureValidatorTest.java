package com.iflytek.skillhub.domain.authoring.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.domain.authoring.FilePatch;
import com.iflytek.skillhub.domain.authoring.FixSuggestion;
import com.iflytek.skillhub.domain.authoring.validation.FindingDraft;
import com.iflytek.skillhub.domain.authoring.validation.ValidationLayer;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class DraftStructureValidatorTest {

    private final DraftStructureValidator validator = new DraftStructureValidator(
            new SkillMetadataParser(), new SkillScaffoldGenerator());

    private static PackageEntry entry(String path, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new PackageEntry(path, bytes, bytes.length, "text/plain");
    }

    @Test
    void validPackageProducesNoFindings() {
        DraftStructureValidator.StructureReport report = validator.validate("demo", "does demo things",
                List.of(entry("SKILL.md", validSkillMd()), entry("scripts/check.sh", "echo OK")));

        assertThat(report.findings()).isEmpty();
        assertThat(report.skillMdPresent()).isTrue();
        assertThat(report.hasErrors()).isFalse();
    }

    @Test
    void missingSkillMdYieldsScaffoldSuggestion() {
        DraftStructureValidator.StructureReport report = validator.validate("demo", "does demo things",
                List.of(entry("scripts/check.sh", "echo OK")));

        assertThat(report.skillMdPresent()).isFalse();
        assertThat(report.hasErrors()).isTrue();
        FindingDraft finding = report.findings().get(0);
        assertThat(finding.ruleCode()).isEqualTo("SKILL_MD_MISSING");
        FixSuggestion suggestion = finding.suggestion();
        assertThat(suggestion).isNotNull();
        assertThat(suggestion.patches()).singleElement().satisfies(patch -> {
            assertThat(patch.filePath()).isEqualTo("SKILL.md");
            assertThat(patch.isCreation()).isTrue();
            assertThat(patch.newValue()).contains("name: demo");
        });
    }

    @Test
    void missingRequiredFrontmatterFieldYieldsInsertPatch() {
        String skillMd = "---\nname: demo\n---\n\n# Demo\n";
        DraftStructureValidator.StructureReport report = validator.validate("demo", null,
                List.of(entry("SKILL.md", skillMd)));

        assertThat(report.hasErrors()).isTrue();
        FindingDraft finding = report.findings().get(0);
        assertThat(finding.ruleCode()).isEqualTo("FRONTMATTER_FIELD_MISSING");
        FixSuggestion suggestion = finding.suggestion();
        assertThat(suggestion).isNotNull();
        FilePatch patch = suggestion.patches().get(0);
        assertThat(patch.filePath()).isEqualTo("SKILL.md");
        assertThat(patch.oldSha256()).isEqualTo(
                com.iflytek.skillhub.domain.authoring.runtime.TaskResult.sha256Hex(skillMd));
        assertThat(patch.oldValue()).isEqualTo(skillMd);
        // the missing field is inserted inside the frontmatter block, keeping valid YAML
        assertThat(patch.newValue()).startsWith("---\n");
        assertThat(patch.newValue()).contains("description: TODO: describe what this skill does");
        assertThat(patch.newValue()).contains("name: demo");
        assertThat(patch.newValue()).endsWith("---\n\n# Demo\n");
    }

    @Test
    void frontmatterWithoutOpeningMarkerIsReportedWithoutSuggestion() {
        DraftStructureValidator.StructureReport report = validator.validate("demo", null,
                List.of(entry("SKILL.md", "name: demo\ndescription: x\n")));

        assertThat(report.hasErrors()).isTrue();
        assertThat(report.findings().get(0).ruleCode()).isEqualTo("FRONTMATTER_MISSING_START");
        assertThat(report.findings().get(0).suggestion()).isNull();
    }

    @Test
    void disallowedExtensionIsAWarning() {
        DraftStructureValidator.StructureReport report = validator.validate("demo", null,
                List.of(entry("SKILL.md", validSkillMd()), entry("binary.exe", "MZ")));

        assertThat(report.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.ruleCode()).isEqualTo("EXTENSION_DISALLOWED");
            assertThat(finding.severity()).isEqualTo(
                    com.iflytek.skillhub.domain.authoring.validation.FindingSeverity.WARNING);
            assertThat(finding.layer()).isEqualTo(ValidationLayer.STRUCTURE);
        });
    }

    @Test
    void invalidPathIsAnError() {
        DraftStructureValidator.StructureReport report = validator.validate("demo", null,
                List.of(entry("SKILL.md", validSkillMd()), entry("../escape.sh", "echo hi")));

        assertThat(report.hasErrors()).isTrue();
        assertThat(report.findings().stream().map(FindingDraft::ruleCode))
                .contains("PATH_INVALID");
    }

    private String validSkillMd() {
        return "---\nname: demo\ndescription: A demo skill\n---\n\n# Demo\n";
    }
}
