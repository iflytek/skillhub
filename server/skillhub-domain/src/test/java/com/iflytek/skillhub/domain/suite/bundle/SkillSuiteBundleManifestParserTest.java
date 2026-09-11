package com.iflytek.skillhub.domain.suite.bundle;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillSuiteBundleManifestParserTest {

    private final SkillSuiteBundleManifestParser parser = new SkillSuiteBundleManifestParser();

    @Test
    void parsesCreateManifestWithPackageAndExactReferenceMembers() {
        SkillSuiteBundleManifest manifest = parser.parse(fixture("valid-create"));

        assertThat(manifest.apiVersion()).isEqualTo("skillhub.iflytek.com/v1alpha1");
        assertThat(manifest.kind()).isEqualTo("SkillSuiteBundle");
        assertThat(manifest.metadata().coordinate().canonical()).isEqualTo("@global/clinical-workflow");
        assertThat(manifest.spec().mode()).isEqualTo(SkillSuiteBundleMode.CREATE);
        assertThat(manifest.spec().baseVersion()).isNull();
        assertThat(manifest.spec().entry().canonical()).isEqualTo("@global/intake");
        assertThat(manifest.spec().members()).hasSize(2);

        SkillSuiteBundleMember packageMember = manifest.spec().members().getFirst();
        assertThat(packageMember.coordinate().canonical()).isEqualTo("@global/intake");
        assertThat(packageMember.packageSource().path()).isEqualTo("skills/intake");
        assertThat(packageMember.packageSource().visibility()).isEqualTo(SkillVisibility.PUBLIC);
        assertThat(packageMember.referenceSource()).isNull();

        SkillSuiteBundleMember referenceMember = manifest.spec().members().get(1);
        assertThat(referenceMember.coordinate().canonical()).isEqualTo("@global/shared-dictionary");
        assertThat(referenceMember.referenceSource().version()).isEqualTo("2.3.1");
        assertThat(referenceMember.packageSource()).isNull();
    }

    @Test
    void parsesUpdateManifestAndKeepsExistingPackageVisibilityUnspecified() {
        SkillSuiteBundleManifest manifest = parser.parse(fixture("valid-update"));

        assertThat(manifest.spec().mode()).isEqualTo(SkillSuiteBundleMode.UPDATE);
        assertThat(manifest.spec().baseVersion()).isEqualTo("1.0.0");
        assertThat(manifest.spec().version()).isEqualTo("1.1.0");
        assertThat(manifest.spec().members().getFirst().packageSource().visibility()).isNull();
        assertThat(manifest.spec().members().get(1).referenceSource().version()).isEqualTo("3.0.0");
    }

    @Test
    void updateMayInheritSummaryAndOverviewButCreateMayNotOmitThem() {
        String update = validManifest()
                .replace("mode: CREATE", "mode: UPDATE\n  baseVersion: 0.9.0")
                .replace("  summary: Valid summary\n", "")
                .replace("  overview: Valid overview\n", "");

        SkillSuiteBundleManifest manifest = parser.parse(update);

        assertThat(manifest.spec().summary()).isNull();
        assertThat(manifest.spec().overview()).isNull();
        assertInvalid(validManifest().replace("  overview: Valid overview\n", ""),
                "spec.overview is required");
    }

    @Test
    void rejectsMemberThatCarriesBothPackageAndReference() {
        assertInvalid(fixture("invalid-both-sources"), "exactly one of package or reference");
    }

    @Test
    void rejectsDangerousPackagePath() {
        assertInvalid(fixture("invalid-dangerous-path"), "unsafe package path");
    }

    @Test
    void rejectsDuplicateLogicalSkillCoordinates() {
        assertInvalid(validManifest().replace(
                "  members:\n    - skill: \"@global/member\"",
                "  members:\n    - skill: \"@global/member\"\n      reference:\n        version: 1.0.0\n    - skill: \"@global/member\""),
                "duplicate member skill");
    }

    @Test
    void rejectsNestedPackageDirectories() {
        String yaml = validManifest().replace(
                "        path: members/member\n        visibility: PUBLIC",
                "        path: members/member\n        visibility: PUBLIC\n"
                        + "    - skill: \"@global/child\"\n"
                        + "      package:\n"
                        + "        path: members/member/child\n"
                        + "        visibility: PUBLIC");

        assertInvalid(yaml, "must not overlap");
    }

    @Test
    void rejectsEntryThatIsNotAMember() {
        assertInvalid(validManifest().replace("entry: \"@global/member\"", "entry: \"@global/missing\""),
                "entry skill must be a member");
    }

    @Test
    void rejectsUpdateWithoutBaseVersionAndCreateWithBaseVersion() {
        assertInvalid(validManifest().replace("mode: CREATE", "mode: UPDATE"),
                "baseVersion is required for UPDATE");
        assertInvalid(validManifest().replace("mode: CREATE", "mode: CREATE\n  baseVersion: 0.9.0"),
                "baseVersion is not allowed for CREATE");
    }

    @Test
    void rejectsVisibilityOnReferenceMember() {
        assertInvalid(validManifest()
                        .replace("package:\n        path: members/member\n        visibility: PUBLIC",
                                "reference:\n        version: latest\n        visibility: PUBLIC"),
                "unknown field");
    }

    @Test
    void rejectsUnsupportedVisibility() {
        assertInvalid(validManifest().replace("visibility: PUBLIC", "visibility: INTERNAL"),
                "unsupported value");
    }

    @Test
    void rejectsUnknownFieldsAndMalformedCoordinates() {
        assertInvalid(validManifest().replace("kind: SkillSuiteBundle", "kind: SkillSuiteBundle\nunexpected: true"),
                "unknown field");
        assertInvalid(validManifest().replace("@global/member", "global/member"),
                "coordinate");
    }

    private void assertInvalid(String yaml, String detail) {
        assertThatThrownBy(() -> parser.parse(yaml))
                .isInstanceOf(DomainBadRequestException.class)
                .satisfies(exception -> {
                    DomainBadRequestException badRequest = (DomainBadRequestException) exception;
                    assertThat(badRequest.messageCode()).isEqualTo("error.suite.bundle.manifest.invalid");
                    assertThat(badRequest.messageArgs()).anySatisfy(argument ->
                            assertThat(argument.toString().toLowerCase()).contains(detail.toLowerCase()));
                });
    }

    private String fixture(String name) {
        String resource = "/suite-bundle/" + name + "/SUITE.yaml";
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing fixture: " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read fixture: " + resource, exception);
        }
    }

    private String validManifest() {
        return """
                apiVersion: skillhub.iflytek.com/v1alpha1
                kind: SkillSuiteBundle
                metadata:
                  namespace: global
                  slug: valid-suite
                spec:
                  mode: CREATE
                  version: 1.0.0
                  displayName: Valid Suite
                  summary: Valid summary
                  overview: Valid overview
                  visibility: PUBLIC
                  entry: "@global/member"
                  members:
                    - skill: "@global/member"
                      package:
                        path: members/member
                        visibility: PUBLIC
                """;
    }
}
