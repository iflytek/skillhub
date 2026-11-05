package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillPackagePreviewServiceTest {
    private final SkillMetadataParser parser = new SkillMetadataParser();
    private final SkillPackagePreviewService service = new SkillPackagePreviewService(new SkillPackageValidator(parser), parser);

    @Test
    void missingDisplayNameUsesPackageNameAndDoesNotInventVersion() {
        var response = service.preview(entries(""));
        assertThat(response.name()).isEqualTo("example-skill");
        assertThat(response.displayName()).isEqualTo("example-skill");
        assertThat(response.version()).isNull();
    }

    @Test
    void previewUsesSameStrictMetadataRulesAsPublish() {
        for (String displayName : List.of("123", "中".repeat(201))) {
            assertThatThrownBy(() -> service.preview(entries("metadata:\n  display-name: " + displayName + "\n")))
                    .isInstanceOf(DomainBadRequestException.class);
        }
    }

    @Test
    void missingSkillMdCannotBePreviewed() {
        assertThatThrownBy(() -> service.preview(List.of()))
                .isInstanceOf(DomainBadRequestException.class);
    }

    @Test
    void oldNullableFieldsKeepTheirSerializationContract() throws Exception {
        String serialized = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(service.preview(entries("")));
        assertThat(serialized).contains("\"version\":null");
    }

    @Test
    void missingOverviewStaysNullSoTheClientCanResolvePackageDocumentation() throws Exception {
        var files = new java.util.ArrayList<>(entries(""));
        byte[] readme = "# User-facing README".getBytes(StandardCharsets.UTF_8);
        files.add(new PackageEntry("README.md", readme, readme.length, "text/markdown"));

        var response = service.preview(files);

        assertThat(response.overview()).isNull();
        assertThat(response.description()).isEqualTo("Summary");
        assertThat(response.shortDescription()).isEqualTo("Summary");
        assertThat(response.examplesCount()).isZero();
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(response))
                .contains("\"overview\":null");
        assertThat(files.get(0).content()).containsExactly(entries("").get(0).content());
        assertThat(files.get(1).content()).containsExactly(readme);
    }

    @Test
    void explicitPackageOverviewIsReturnedWithoutUsingTheSkillBody() {
        var response = service.preview(entries("metadata:\n  overview: '  Package overview  '\n"));

        assertThat(response.overview()).isEqualTo("Package overview");
    }

    @Test
    void blankPackageOverviewDoesNotBecomeTheSkillBody() {
        for (String metadata : List.of("metadata: {}\n", "metadata:\n  overview: null\n",
                "metadata:\n  overview: '   '\n")) {
            assertThat(service.preview(entries(metadata)).overview()).isNull();
        }
    }

    @Test
    void examplesRecognitionIsNonBlockingAndOmitsInvalidCount() throws Exception {
        assertThat(service.preview(entries("")).examplesCount()).isZero();
        for (String content : List.of("{not json", "{\"schemaVersion\":1,\"defaultLocale\":\"en\",\"examples\":{\"en\":[]}}")) {
            var files = new java.util.ArrayList<>(entries(""));
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            files.add(new PackageEntry(".skillhub/examples.json", bytes, bytes.length, "application/json"));
            var response = service.preview(files);
            assertThat(response.examplesStatus().name()).isEqualTo(content.equals("{not json") ? "invalid" : "none");
            if (content.equals("{not json")) {
                assertThat(response.examplesCount()).isNull();
                assertThat(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(response)).doesNotContain("examplesCount");
            } else assertThat(response.examplesCount()).isZero();
            assertThat(files.get(1).content()).containsExactly(bytes);
        }
    }

    @Test
    void previewRejectsInstallerRecordsUsingTheSharedPublishPolicy() {
        var files = new java.util.ArrayList<>(entries(""));
        files.add(new PackageEntry(".skillhub/metadata.json", new byte[0], 0, "application/json"));
        assertThatThrownBy(() -> service.preview(files)).isInstanceOf(DomainBadRequestException.class);
    }

    private List<PackageEntry> entries(String extra) {
        byte[] bytes = ("---\nname: example-skill\ndescription: Summary\n" + extra + "---\nBody").getBytes(StandardCharsets.UTF_8);
        return List.of(new PackageEntry("SKILL.md", bytes, bytes.length, "text/markdown"));
    }
}
