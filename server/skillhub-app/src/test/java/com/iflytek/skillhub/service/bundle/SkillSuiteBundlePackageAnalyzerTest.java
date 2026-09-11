package com.iflytek.skillhub.service.bundle;

import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy;
import com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleManifestParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillSuiteBundlePackageAnalyzerTest {

    private final SkillMetadataParser metadataParser = new SkillMetadataParser();
    private final SkillSuiteBundlePackageAnalyzer analyzer = new SkillSuiteBundlePackageAnalyzer(
            new SkillSuiteBundleManifestParser(), metadataParser, new SkillPackageValidator(metadataParser));

    @Test
    void analyzesAnOuterDirectoryWithoutMixingMemberPackages() throws Exception {
        List<SkillSuiteBundleStagedEntry> entries = List.of(
                entry("bundle/SUITE.yaml", manifest("""
                          - skill: "@global/first"
                            package:
                              path: skills/first
                              visibility: PUBLIC
                          - skill: "@global/second"
                            package:
                              path: skills/second
                              visibility: PUBLIC
                          - skill: "@global/shared"
                            reference:
                              version: 2.0.0
                        """, "@global/first")),
                entry("bundle/skills/first/SKILL.md", skillMd("first", "1.0.0")),
                entry("bundle/skills/first/notes.txt", "first"),
                entry("bundle/skills/second/SKILL.md", skillMd("second", "1.1.0")),
                entry("bundle/skills/second/notes.txt", "second"),
                entry("__MACOSX/._SUITE.yaml", "ignored")
        );

        SkillSuiteBundlePackageAnalyzer.BundleAnalysis result = analyzer.analyze(entries);

        assertThat(result.errors()).isEmpty();
        assertThat(result.packageMembers()).hasSize(2);
        assertThat(result.packageMembers().getFirst().files())
                .extracting(SkillSuiteBundlePackageAnalyzer.StagedMemberFile::relativePath)
                .containsExactly("SKILL.md", "notes.txt");
        assertThat(result.packageMembers().get(1).files())
                .extracting(SkillSuiteBundlePackageAnalyzer.StagedMemberFile::relativePath)
                .containsExactly("SKILL.md", "notes.txt");
        assertThat(result.packageMembers()).allSatisfy(member -> {
            assertThat(member.validation().passed()).isTrue();
            assertThat(member.fingerprint()).startsWith("sha256:");
        });
    }

    @Test
    void reportsMissingRootSkillMdAndDoesNotPromoteNestedOne() throws Exception {
        SkillSuiteBundlePackageAnalyzer.BundleAnalysis result = analyzer.analyze(List.of(
                entry("SUITE.yaml", manifest(packageMember("missing"), "@global/missing")),
                entry("skills/missing/docs/SKILL.md", skillMd("missing", "1.0.0"))
        ));

        assertThat(result.packageMembers()).singleElement().satisfies(member ->
                assertThat(member.validation().errors())
                        .contains("Missing required file: SKILL.md at root")
                        .anyMatch(error -> error.contains("Nested SKILL.md is not allowed")));
    }

    @Test
    void rejectsDuplicateCanonicalArchivePaths() throws Exception {
        SkillSuiteBundlePackageAnalyzer.BundleAnalysis result = analyzer.analyze(List.of(
                entry("SUITE.yaml", manifest(packageMember("duplicate"), "@global/duplicate")),
                entry("skills/duplicate/SKILL.md", skillMd("duplicate", "1.0.0")),
                entry("skills/duplicate/skill.md", skillMd("duplicate", "1.0.0"))
        ));

        assertThat(result.errors()).anyMatch(error -> error.contains("Duplicate archive path"));
    }

    @Test
    void rejectsFilesOutsideDeclaredMemberDirectories() throws Exception {
        SkillSuiteBundlePackageAnalyzer.BundleAnalysis result = analyzer.analyze(List.of(
                entry("SUITE.yaml", manifest(packageMember("declared"), "@global/declared")),
                entry("skills/declared/SKILL.md", skillMd("declared", "1.0.0")),
                entry("skills/other/SKILL.md", skillMd("other", "1.0.0")),
                entry("README.md", "unclaimed")
        ));

        assertThat(result.errors())
                .anyMatch(error -> error.contains("Unclaimed archive entry: skills/other/SKILL.md"))
                .anyMatch(error -> error.contains("Unclaimed archive entry: README.md"));
    }

    @Test
    void reportsManifestAndSkillMetadataConflict() throws Exception {
        SkillSuiteBundlePackageAnalyzer.BundleAnalysis result = analyzer.analyze(List.of(
                entry("SUITE.yaml", manifest(packageMember("expected"), "@global/expected")),
                entry("skills/expected/SKILL.md", skillMd("different-name", "1.0.0"))
        ));

        assertThat(result.packageMembers()).singleElement().satisfies(member ->
                assertThat(member.validation().errors())
                        .anyMatch(error -> error.contains("does not match manifest skill @global/expected")));
    }

    @Test
    void appliesOrdinarySingleSkillFileLimitsPerMember() throws Exception {
        SkillPackageValidator oneFileValidator = new SkillPackageValidator(
                metadataParser, 1, SkillPackagePolicy.MAX_SINGLE_FILE_SIZE,
                SkillPackagePolicy.MAX_TOTAL_PACKAGE_SIZE, SkillPackagePolicy.ALLOWED_EXTENSIONS);
        SkillSuiteBundlePackageAnalyzer constrained = new SkillSuiteBundlePackageAnalyzer(
                new SkillSuiteBundleManifestParser(), metadataParser, oneFileValidator);

        SkillSuiteBundlePackageAnalyzer.BundleAnalysis result = constrained.analyze(List.of(
                entry("SUITE.yaml", manifest(packageMember("limited"), "@global/limited")),
                entry("skills/limited/SKILL.md", skillMd("limited", "1.0.0")),
                entry("skills/limited/extra.txt", "extra")
        ));

        assertThat(result.packageMembers()).singleElement().satisfies(member ->
                assertThat(member.validation().errors()).contains("Too many files: 2 (max: 1)"));
    }

    private SkillSuiteBundleStagedEntry entry(String path, String content) throws Exception {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        return new SkillSuiteBundleStagedEntry(
                path, bytes.length, "text/plain", sha256, "temporary/" + path,
                () -> new ByteArrayInputStream(bytes));
    }

    private String packageMember(String slug) {
        return """
                  - skill: "@global/%s"
                    package:
                      path: skills/%s
                      visibility: PUBLIC
                """.formatted(slug, slug);
    }

    private String manifest(String members, String entry) {
        return """
                apiVersion: skillhub.iflytek.com/v1alpha1
                kind: SkillSuiteBundle
                metadata:
                  namespace: global
                  slug: test-suite
                spec:
                  mode: CREATE
                  version: 1.0.0
                  displayName: Test Suite
                  summary: Test summary
                  overview: Test overview
                  visibility: PUBLIC
                  entry: "%s"
                  members:
                %s
                """.formatted(entry, indent(members, 4));
    }

    private String indent(String value, int spaces) {
        String prefix = " ".repeat(spaces);
        List<String> lines = new ArrayList<>();
        value.stripTrailing().lines().forEach(line -> lines.add(prefix + line));
        return String.join("\n", lines);
    }

    private String skillMd(String name, String version) {
        return """
                ---
                name: %s
                description: Test skill
                version: %s
                ---
                Test instructions.
                """.formatted(name, version);
    }
}
