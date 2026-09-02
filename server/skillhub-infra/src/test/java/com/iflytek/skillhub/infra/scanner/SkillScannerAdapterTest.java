package com.iflytek.skillhub.infra.scanner;

import com.iflytek.skillhub.domain.security.SecurityFinding;
import com.iflytek.skillhub.domain.security.SecurityScanRequest;
import com.iflytek.skillhub.domain.security.SecurityScanResponse;
import com.iflytek.skillhub.domain.security.SecurityVerdict;
import com.iflytek.skillhub.infra.http.HttpClient;
import com.iflytek.skillhub.infra.http.HttpClientException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillScannerAdapterTest {

    @Test
    void scan_localModeCallsDirectoryEndpointAndMapsDangerousVerdict() {
        StubSkillScannerService skillScannerService = new StubSkillScannerService();
        skillScannerService.directoryResponse = new SkillScannerApiResponse(
                "scan-1",
                "test-skill",
                false,
                "HIGH",
                1,
                List.of(new SkillScannerApiResponse.Finding(
                        "STATIC-001_abc123",
                        "STATIC-001",
                        "HIGH",
                        "code-execution",
                        "Dynamic execution",
                        "avoid eval",
                        "src/main.py",
                        12,
                        "eval(user_input)",
                        "Use ast.literal_eval instead",
                        "static",
                        Map.of()
                )),
                1.25,
                "2026-03-22T07:00:00"
        );
        ScanOptions options = ScanOptions.disabled();
        SkillScannerAdapter adapter = new SkillScannerAdapter(skillScannerService, "local", options);

        SecurityScanResponse response = adapter.scan(new SecurityScanRequest("task-1", 42L, "/tmp/skill", Map.of()));

        assertThat(skillScannerService.lastDirectoryPath).isEqualTo("/tmp/skill");
        assertThat(response.scanId()).isEqualTo("scan-1");
        assertThat(response.verdict()).isEqualTo(SecurityVerdict.DANGEROUS);
        assertThat(response.findingsCount()).isEqualTo(1);

        SecurityFinding finding = response.findings().get(0);
        assertThat(finding.ruleId()).isEqualTo("STATIC-001");
        assertThat(finding.message()).isEqualTo("avoid eval");
        assertThat(finding.filePath()).isEqualTo("src/main.py");
        assertThat(finding.lineNumber()).isEqualTo(12);
        assertThat(finding.codeSnippet()).isEqualTo("eval(user_input)");
        assertThat(finding.remediation()).isEqualTo("Use ast.literal_eval instead");
        assertThat(finding.analyzer()).isEqualTo("static");
        assertThat(finding.metadata()).isEmpty();
    }

    @Test
    void scan_uploadModeCallsUploadEndpointAndMapsBlockedVerdict() {
        StubSkillScannerService skillScannerService = new StubSkillScannerService();
        skillScannerService.uploadResponse = new SkillScannerApiResponse(
                "scan-2",
                "test-skill",
                false,
                "CRITICAL",
                2,
                List.of(),
                2.0,
                "2026-03-22T07:00:00"
        );
        ScanOptions options = ScanOptions.disabled();
        SkillScannerAdapter adapter = new SkillScannerAdapter(skillScannerService, "upload", options);

        SecurityScanResponse response = adapter.scan(new SecurityScanRequest("task-1", 42L, "/tmp/skill.zip", Map.of()));

        assertThat(skillScannerService.lastUploadPath).isEqualTo(Path.of("/tmp/skill.zip"));
        assertThat(response.verdict()).isEqualTo(SecurityVerdict.BLOCKED);
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 599})
    void scan_treatsTransientHttpFailureAsScannerUnavailable(int statusCode) {
        StubSkillScannerService skillScannerService = new StubSkillScannerService();
        skillScannerService.directoryException = new HttpClientException(statusCode, "scanner unavailable");
        ScanOptions options = ScanOptions.disabled();
        SkillScannerAdapter adapter = new SkillScannerAdapter(skillScannerService, "local", options);

        assertThatThrownBy(() -> adapter.scan(new SecurityScanRequest("task-1", 42L, "/tmp/skill", Map.of())))
                .isInstanceOf(SecurityScanException.class)
                .hasMessage("Security scan request failed: HTTP " + statusCode + ": scanner unavailable")
                .satisfies(error -> assertThat(((SecurityScanException) error).isScannerUnavailable()).isTrue());
    }

    @Test
    void scan_treatsConnectionFailureAsScannerUnavailable() {
        StubSkillScannerService skillScannerService = new StubSkillScannerService();
        skillScannerService.directoryException =
                new HttpClientException("request failed", new IllegalStateException("connection refused"));
        SkillScannerAdapter adapter = new SkillScannerAdapter(
                skillScannerService, "local", ScanOptions.disabled());

        assertThatThrownBy(() -> adapter.scan(new SecurityScanRequest("task-1", 42L, "/tmp/skill", Map.of())))
                .isInstanceOf(SecurityScanException.class)
                .satisfies(error -> assertThat(((SecurityScanException) error).isScannerUnavailable()).isTrue());
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 422, 499})
    void scan_treatsDeterministicClientFailureAsPermanent(int statusCode) {
        StubSkillScannerService skillScannerService = new StubSkillScannerService();
        skillScannerService.directoryException = new HttpClientException(statusCode, "invalid package");
        SkillScannerAdapter adapter = new SkillScannerAdapter(
                skillScannerService, "local", ScanOptions.disabled());

        assertThatThrownBy(() -> adapter.scan(
                new SecurityScanRequest("task-1", 42L, "/tmp/skill", Map.of())))
                .isInstanceOf(SecurityScanException.class)
                .satisfies(error -> assertThat(((SecurityScanException) error).isScannerUnavailable()).isFalse());
    }

    private static final class StubSkillScannerService extends SkillScannerService {
        private String lastDirectoryPath;
        private Path lastUploadPath;
        private SkillScannerApiResponse directoryResponse;
        private SkillScannerApiResponse uploadResponse;
        private RuntimeException directoryException;

        private StubSkillScannerService() {
            super(new NoOpHttpClient(), "http://scanner.test", "/scan-upload", "/health");
        }

        @Override
        public SkillScannerApiResponse scanDirectory(String skillDirectory, ScanOptions options) {
            this.lastDirectoryPath = skillDirectory;
            if (directoryException != null) {
                throw directoryException;
            }
            return directoryResponse;
        }

        @Override
        public SkillScannerApiResponse scanUpload(Path skillPackagePath, ScanOptions options) {
            this.lastUploadPath = skillPackagePath;
            return uploadResponse;
        }
    }

    private static final class NoOpHttpClient implements HttpClient {
        @Override
        public <T> T get(String uri, Class<T> responseType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T post(String uri, Object body, Class<T> responseType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T postMultipart(String uri, org.springframework.util.MultiValueMap<String, Object> parts,
                                   Class<T> responseType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T postMultipart(String uri,
                                   org.springframework.util.MultiValueMap<String, Object> parts,
                                   HttpHeaders headers,
                                   Class<T> responseType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isHealthy(String healthUri) {
            return false;
        }
    }
}
