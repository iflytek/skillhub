package com.iflytek.skillhub.controller.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.SkillPublishProperties;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MultipartPackageExtractorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MultipartPackageExtractor extractor(SkillPublishProperties props) {
        return new MultipartPackageExtractor(props, objectMapper);
    }

    private static void invokeNormalizePath(MultipartPackageExtractor extractor, String path) throws Exception {
        Method method = MultipartPackageExtractor.class.getDeclaredMethod("normalizePath", String.class);
        method.setAccessible(true);
        try {
            method.invoke(extractor, path);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw ex;
        }
    }

    private static SkillPublishProperties defaultProps() {
        SkillPublishProperties p = new SkillPublishProperties();
        p.setMaxFileCount(10);
        p.setMaxPackageSize(4096);
        p.setMaxSingleFileSize(1024);
        return p;
    }

    private String payloadJson() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "namespace", "global",
                "slug", "demo",
                "displayName", "Demo",
                "version", "1.0.0",
                "changelog", "init"
        ));
    }

    private static void assertBadRequest(Throwable error, String expectedMessageArg) {
        DomainBadRequestException exception = (DomainBadRequestException) error;
        assertThat(exception.messageCode()).isEqualTo("error.skill.publish.package.invalid");
        assertThat(exception.messageArgs()).containsExactly(expectedMessageArg);
    }

    @Test
    void extract_parsesPayloadAndFiles() throws Exception {
        SkillPublishProperties props = defaultProps();
        MockMultipartFile file = new MockMultipartFile("files", "README.md", "text/plain", "# Hello".getBytes());

        MultipartPackageExtractor.ExtractedPackage result = extractor(props).extract(new MockMultipartFile[]{file}, payloadJson());

        assertThat(result.payload().slug()).isEqualTo("demo");
        assertThat(result.entries()).hasSize(1);
        assertThat(result.entries().get(0).path()).isEqualTo("README.md");
        assertThat(result.entries().get(0).contentType()).isEqualTo("text/markdown");
    }

    @Test
    void extract_skipsNullAndBlankFilenames() throws Exception {
        SkillPublishProperties props = defaultProps();
        MockMultipartFile blank = new MockMultipartFile("files", "", "text/plain", "x".getBytes());
        MockMultipartFile nullName = new MockMultipartFile("files", (String) null, "text/plain", "y".getBytes());
        MockMultipartFile valid = new MockMultipartFile("files", "a.txt", "text/plain", "z".getBytes());

        MultipartPackageExtractor.ExtractedPackage result = extractor(props).extract(new MockMultipartFile[]{blank, nullName, valid}, payloadJson());

        assertThat(result.entries()).hasSize(1);
        assertThat(result.entries().get(0).path()).isEqualTo("a.txt");
    }

    @Test
    void extract_rejectsTooManyFiles() throws Exception {
        SkillPublishProperties props = defaultProps();
        props.setMaxFileCount(1);
        MockMultipartFile a = new MockMultipartFile("files", "a.txt", "text/plain", "a".getBytes());
        MockMultipartFile b = new MockMultipartFile("files", "b.txt", "text/plain", "b".getBytes());

        assertThatThrownBy(() -> extractor(props).extract(new MockMultipartFile[]{a, b}, payloadJson()))
                .isInstanceOf(DomainBadRequestException.class)
                .satisfies(error -> assertBadRequest(error, "Too many files: max 1"));
    }

    @Test
    void extract_rejectsDuplicatePaths() throws Exception {
        SkillPublishProperties props = defaultProps();
        MockMultipartFile a = new MockMultipartFile("files", "a.txt", "text/plain", "a".getBytes());
        MockMultipartFile b = new MockMultipartFile("files", "a.txt", "text/plain", "b".getBytes());

        assertThatThrownBy(() -> extractor(props).extract(new MockMultipartFile[]{a, b}, payloadJson()))
                .isInstanceOf(DomainBadRequestException.class)
                .satisfies(error -> assertBadRequest(error, "Duplicate package path: a.txt"));
    }

    @Test
    void extract_rejectsOversizedPackage() throws Exception {
        SkillPublishProperties props = defaultProps();
        props.setMaxPackageSize(5);
        MockMultipartFile a = new MockMultipartFile("files", "a.txt", "text/plain", "123".getBytes());
        MockMultipartFile b = new MockMultipartFile("files", "b.txt", "text/plain", "456".getBytes());

        assertThatThrownBy(() -> extractor(props).extract(new MockMultipartFile[]{a, b}, payloadJson()))
                .isInstanceOf(DomainBadRequestException.class)
                .satisfies(error -> assertBadRequest(error, "Package too large: max 5 bytes"));
    }

    @Test
    void extract_rejectsOversizedSingleFile() throws Exception {
        SkillPublishProperties props = defaultProps();
        props.setMaxSingleFileSize(3);
        MockMultipartFile a = new MockMultipartFile("files", "large.txt", "text/plain", "1234".getBytes());

        assertThatThrownBy(() -> extractor(props).extract(new MockMultipartFile[]{a}, payloadJson()))
                .isInstanceOf(DomainBadRequestException.class)
                .satisfies(error -> assertBadRequest(error, "File too large: large.txt (max 3 bytes)"));
    }

    @Test
    void extract_normalizesBackslashToSlash() throws Exception {
        SkillPublishProperties props = defaultProps();
        MockMultipartFile file = new MockMultipartFile("files", "dir\\file.txt", "text/plain", "x".getBytes());

        MultipartPackageExtractor.ExtractedPackage result = extractor(props).extract(new MockMultipartFile[]{file}, payloadJson());

        assertThat(result.entries().get(0).path()).isEqualTo("dir/file.txt");
    }

    @Test
    void extract_stripsLeadingDotSlash() throws Exception {
        SkillPublishProperties props = defaultProps();
        MockMultipartFile file = new MockMultipartFile("files", "./file.txt", "text/plain", "x".getBytes());

        MultipartPackageExtractor.ExtractedPackage result = extractor(props).extract(new MockMultipartFile[]{file}, payloadJson());

        assertThat(result.entries().get(0).path()).isEqualTo("file.txt");
    }

    @Test
    void extract_rejectsUnsafePaths() {
        SkillPublishProperties props = defaultProps();
        List<String> unsafePaths = List.of("../secret.txt", "..", "/etc/passwd", "a//b.txt", "./");

        for (String unsafePath : unsafePaths) {
            MockMultipartFile file = new MockMultipartFile("files", unsafePath, "text/plain", "x".getBytes());
            assertThatThrownBy(() -> extractor(props).extract(new MockMultipartFile[]{file}, "{}"))
                    .as("path: %s", unsafePath)
                    .isInstanceOf(DomainBadRequestException.class);
        }
    }

    @Test
    void extract_returnsEmptyEntries_whenFilesArrayIsNull() throws Exception {
        SkillPublishProperties props = defaultProps();

        MultipartPackageExtractor.ExtractedPackage result = extractor(props).extract(null, payloadJson());

        assertThat(result.entries()).isEmpty();
    }

    @Test
    void determineContentType_coversAllExtensions() throws Exception {
        SkillPublishProperties props = defaultProps();
        MultipartPackageExtractor ext = extractor(props);

        Map<String, String> expectations = Map.of(
                "a.py", "text/x-python",
                "a.json", "application/json",
                "a.yaml", "application/x-yaml",
                "a.yml", "application/x-yaml",
                "a.txt", "text/plain",
                "a.md", "text/markdown",
                "a.bin", "application/octet-stream"
        );

        for (Map.Entry<String, String> e : expectations.entrySet()) {
            MockMultipartFile file = new MockMultipartFile("files", e.getKey(), "application/octet-stream", "x".getBytes());
            MultipartPackageExtractor.ExtractedPackage result = ext.extract(new MockMultipartFile[]{file}, payloadJson());
            assertThat(result.entries().get(0).contentType()).as("file: %s", e.getKey()).isEqualTo(e.getValue());
        }
    }

    @Test
    void forkOfRecordIsInstantiable() {
        MultipartPackageExtractor.PublishPayload.ForkOf forkOf = new MultipartPackageExtractor.PublishPayload.ForkOf("slug", "1.0.0");
        assertThat(forkOf.slug()).isEqualTo("slug");
        assertThat(forkOf.version()).isEqualTo("1.0.0");
    }

    @Test
    void normalizePath_rejectsBlankPath() throws Exception {
        MultipartPackageExtractor ext = extractor(defaultProps());

        assertThatThrownBy(() -> invokeNormalizePath(ext, " "))
                .isInstanceOf(DomainBadRequestException.class)
                .satisfies(error -> assertBadRequest(error, "Package entry path is blank"));
    }
}
