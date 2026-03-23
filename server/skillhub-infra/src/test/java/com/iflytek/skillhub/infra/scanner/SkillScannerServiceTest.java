package com.iflytek.skillhub.infra.scanner;

import com.iflytek.skillhub.infra.http.HttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.util.MultiValueMap;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SkillScannerServiceTest {

    @Test
    @DisplayName("constructor rejects scanner base URLs with unsafe components")
    void constructor_rejectsUnsafeBaseUrl() {
        FakeHttpClient httpClient = new FakeHttpClient();

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new SkillScannerService(httpClient, "file:///tmp/scanner", "/scan-upload", "/health"))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new SkillScannerService(httpClient, "http://user:secret@scanner.test", "/scan-upload", "/health"))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new SkillScannerService(httpClient, "http://scanner.test?x=1", "/scan-upload", "/health"))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new SkillScannerService(httpClient, "http://scanner.test#frag", "/scan-upload", "/health"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scanDirectory_postsToLocalScanEndpoint() {
        FakeHttpClient httpClient = new FakeHttpClient();
        SkillScannerApiResponse apiResponse = new SkillScannerApiResponse(
                "scan-1",
                "test-skill",
                false,
                "HIGH",
                2,
                null,
                1.5,
                "2026-03-22T07:00:00"
        );
        httpClient.postResponse = apiResponse;
        SkillScannerService service = new SkillScannerService(
                httpClient,
                "http://scanner.test",
                "/scan-upload",
                "/health"
        );
        ScanOptions options = new ScanOptions(true, false, "anthropic", false, false, "", false, false);

        SkillScannerApiResponse response = service.scanDirectory("/tmp/demo", options);

        assertThat(response).isEqualTo(apiResponse);
        assertThat(httpClient.lastPostUri).isEqualTo("http://scanner.test/scan");
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) httpClient.lastPostBody;
        assertThat(body.get("skill_directory")).isEqualTo("/tmp/demo");
        assertThat(body.get("use_behavioral")).isEqualTo(true);
        assertThat(body.get("use_llm")).isEqualTo(false);
    }

    @Test
    void scanUpload_postsMultipartToConfiguredUploadEndpoint() {
        FakeHttpClient httpClient = new FakeHttpClient();
        SkillScannerApiResponse apiResponse = new SkillScannerApiResponse(
                "scan-2",
                "test-skill",
                true,
                "LOW",
                0,
                null,
                0.5,
                "2026-03-22T07:00:00"
        );
        httpClient.multipartResponse = apiResponse;
        SkillScannerService service = new SkillScannerService(
                httpClient,
                "http://scanner.test",
                "/scan-upload",
                "/health"
        );
        ScanOptions options = new ScanOptions(false, true, "openai", true, false, "", false, false);

        SkillScannerApiResponse response = service.scanUpload(Path.of("/tmp/demo.zip"), options);

        assertThat(response).isEqualTo(apiResponse);
        assertThat(httpClient.lastMultipartUri).startsWith("http://scanner.test/scan-upload?");
        assertThat(httpClient.lastMultipartUri).contains("use_llm=true");
        assertThat(httpClient.lastMultipartUri).contains("llm_provider=openai");
        assertThat(httpClient.lastMultipartParts.getFirst("file")).isNotNull();
    }

    @Test
    void scanUpload_sendsAidefenseApiKeyViaHeaderInsteadOfQueryString() {
        FakeHttpClient httpClient = new FakeHttpClient();
        httpClient.multipartResponse = new SkillScannerApiResponse(
                "scan-3",
                "test-skill",
                true,
                "LOW",
                0,
                null,
                0.5,
                "2026-03-22T07:00:00"
        );
        SkillScannerService service = new SkillScannerService(
                httpClient,
                "http://scanner.test",
                "/scan-upload",
                "/health"
        );
        ScanOptions options = new ScanOptions(false, true, "openai", true, true, "secret-key", false, false);

        service.scanUpload(Path.of("/tmp/demo.zip"), options);

        assertThat(httpClient.lastMultipartUri).doesNotContain("aidefense_api_key");
        assertThat(httpClient.lastMultipartHeaders.getFirst("X-AIDefense-Api-Key")).isEqualTo("secret-key");
    }

    @Test
    void isHealthy_checksConfiguredHealthEndpoint() {
        FakeHttpClient httpClient = new FakeHttpClient();
        httpClient.healthy = true;
        SkillScannerService service = new SkillScannerService(
                httpClient,
                "http://scanner.test",
                "/scan-upload",
                "/health"
        );

        boolean healthy = service.isHealthy();

        assertThat(healthy).isTrue();
        assertThat(httpClient.lastHealthUri).isEqualTo("http://scanner.test/health");
    }

    private static final class FakeHttpClient implements HttpClient {
        private Object postResponse;
        private Object multipartResponse;
        private String lastPostUri;
        private Object lastPostBody;
        private String lastMultipartUri;
        private MultiValueMap<String, Object> lastMultipartParts;
        private HttpHeaders lastMultipartHeaders;
        private String lastHealthUri;
        private boolean healthy;

        @Override
        public <T> T get(String uri, Class<T> responseType) {
            throw new UnsupportedOperationException();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T post(String uri, Object body, Class<T> responseType) {
            this.lastPostUri = uri;
            this.lastPostBody = body;
            return (T) postResponse;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T postMultipart(String uri, MultiValueMap<String, Object> parts, Class<T> responseType) {
            return postMultipart(uri, parts, new HttpHeaders(), responseType);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T postMultipart(String uri,
                                   MultiValueMap<String, Object> parts,
                                   HttpHeaders headers,
                                   Class<T> responseType) {
            this.lastMultipartUri = uri;
            this.lastMultipartParts = parts;
             this.lastMultipartHeaders = headers;
            return (T) multipartResponse;
        }

        @Override
        public boolean isHealthy(String healthUri) {
            this.lastHealthUri = healthUri;
            return healthy;
        }
    }
}
