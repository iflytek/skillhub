package com.iflytek.skillhub.controller.support;

import com.iflytek.skillhub.config.SkillPublishProperties;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZipPackageExtractorTest {

    @Test
    void extract_stripsSingleRootDirectoryAndAssignsContentTypes() throws Exception {
        ZipPackageExtractor extractor = new ZipPackageExtractor(defaultProperties());
        MockMultipartFile file = zipFile(Map.of(
                "demo-skill/SKILL.md", "# title",
                "demo-skill/config/settings.json", "{\"a\":1}"
        ));

        List<PackageEntry> entries = extractor.extract(file);

        assertThat(entries).hasSize(2);
        assertThat(entries)
                .extracting(PackageEntry::path)
                .containsExactlyInAnyOrder("SKILL.md", "config/settings.json");
        assertThat(entries)
                .filteredOn(entry -> entry.path().equals("SKILL.md"))
                .singleElement()
                .extracting(PackageEntry::contentType)
                .isEqualTo("text/markdown");
        assertThat(entries)
                .filteredOn(entry -> entry.path().equals("config/settings.json"))
                .singleElement()
                .extracting(PackageEntry::contentType)
                .isEqualTo("application/json");
    }

    @Test
    void extract_rejectsDuplicatePaths() throws Exception {
        ZipPackageExtractor extractor = new ZipPackageExtractor(defaultProperties());
        MockMultipartFile file = zipFile(List.of(
                new ZipSpec("nested/../a.txt", "first"),
                new ZipSpec("a.txt", "second")
        ));

        assertThatThrownBy(() -> extractor.extract(file))
                .isInstanceOf(DomainBadRequestException.class)
                .satisfies(error -> {
                    DomainBadRequestException exception = (DomainBadRequestException) error;
                    assertThat(exception.messageCode()).isEqualTo("error.skill.publish.package.invalid");
                    assertThat(exception.messageArgs()).containsExactly("Duplicate package path: a.txt");
                });
    }

    @Test
    void extract_rejectsUnsafePath() throws Exception {
        ZipPackageExtractor extractor = new ZipPackageExtractor(defaultProperties());
        MockMultipartFile file = zipFile(Map.of("../secret.txt", "boom"));

        assertThatThrownBy(() -> extractor.extract(file))
                .isInstanceOf(DomainBadRequestException.class)
                .satisfies(error -> {
                    DomainBadRequestException exception = (DomainBadRequestException) error;
                    assertThat(exception.messageCode()).isEqualTo("error.skill.publish.package.invalid");
                    assertThat(exception.messageArgs()).containsExactly("Unsafe package path: ../secret.txt");
                });
    }

    @Test
    void extract_rejectsBackslashSeparators() throws Exception {
        ZipPackageExtractor extractor = new ZipPackageExtractor(defaultProperties());
        MockMultipartFile file = zipFile(Map.of("demo\\skill.txt", "boom"));

        assertThatThrownBy(() -> extractor.extract(file))
                .isInstanceOf(DomainBadRequestException.class)
                .satisfies(error -> {
                    DomainBadRequestException exception = (DomainBadRequestException) error;
                    assertThat(exception.messageCode()).isEqualTo("error.skill.publish.package.invalid");
                    assertThat(exception.messageArgs()).containsExactly("Package entry must use '/' separators: demo\\skill.txt");
                });
    }

    @Test
    void extract_rejectsTooManyFiles() throws Exception {
        SkillPublishProperties properties = defaultProperties();
        properties.setMaxFileCount(1);
        ZipPackageExtractor extractor = new ZipPackageExtractor(properties);
        MockMultipartFile file = zipFile(Map.of(
                "a.txt", "one",
                "b.txt", "two"
        ));

        assertThatThrownBy(() -> extractor.extract(file))
                .isInstanceOf(DomainBadRequestException.class)
                .satisfies(error -> {
                    DomainBadRequestException exception = (DomainBadRequestException) error;
                    assertThat(exception.messageCode()).isEqualTo("error.skill.publish.package.invalid");
                    assertThat(exception.messageArgs()).containsExactly("Too many files: max 1");
                });
    }

    @Test
    void extract_rejectsOversizedSingleFile() throws Exception {
        SkillPublishProperties properties = defaultProperties();
        properties.setMaxSingleFileSize(3);
        ZipPackageExtractor extractor = new ZipPackageExtractor(properties);
        MockMultipartFile file = zipFile(Map.of("large.txt", "1234"));

        assertThatThrownBy(() -> extractor.extract(file))
                .isInstanceOf(DomainBadRequestException.class)
                .satisfies(error -> {
                    DomainBadRequestException exception = (DomainBadRequestException) error;
                    assertThat(exception.messageCode()).isEqualTo("error.skill.publish.package.invalid");
                    assertThat(exception.messageArgs()).containsExactly("File too large: large.txt (max 3 bytes)");
                });
    }

    @Test
    void extract_rejectsOversizedPackage() throws Exception {
        SkillPublishProperties properties = defaultProperties();
        properties.setMaxPackageSize(5);
        ZipPackageExtractor extractor = new ZipPackageExtractor(properties);
        MockMultipartFile file = zipFile(Map.of(
                "a.txt", "123",
                "b.txt", "456"
        ));

        assertThatThrownBy(() -> extractor.extract(file))
                .isInstanceOf(DomainBadRequestException.class)
                .satisfies(error -> {
                    DomainBadRequestException exception = (DomainBadRequestException) error;
                    assertThat(exception.messageCode()).isEqualTo("error.skill.publish.package.invalid");
                    assertThat(exception.messageArgs()).containsExactly("Package too large: max 5 bytes");
                });
    }

    private static SkillPublishProperties defaultProperties() {
        SkillPublishProperties properties = new SkillPublishProperties();
        properties.setMaxFileCount(10);
        properties.setMaxSingleFileSize(1024);
        properties.setMaxPackageSize(4096);
        return properties;
    }

    private static MockMultipartFile zipFile(Map<String, String> entries) throws IOException {
        return zipFile(entries.entrySet().stream()
                .map(entry -> new ZipSpec(entry.getKey(), entry.getValue()))
                .toList());
    }

    private static MockMultipartFile zipFile(List<ZipSpec> entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (ZipSpec entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.path()));
                zip.write(entry.content().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return new MockMultipartFile("file", "skill.zip", "application/zip", output.toByteArray());
    }

    private record ZipSpec(String path, String content) {
    }
}
