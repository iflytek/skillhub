package com.iflytek.skillhub.infra.scanner;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.iflytek.skillhub.domain.security.SecurityScanRequest;
import com.iflytek.skillhub.infra.http.HttpClient;
import com.iflytek.skillhub.infra.http.HttpClientException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.util.MultiValueMap;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillScannerLoggingTest {

    private final Logger serviceLogger = (Logger) LoggerFactory.getLogger(SkillScannerService.class);
    private final Logger adapterLogger = (Logger) LoggerFactory.getLogger(SkillScannerAdapter.class);
    private ListAppender<ILoggingEvent> appender;

    @AfterEach
    void tearDown() {
        if (appender != null) {
            serviceLogger.detachAppender(appender);
            adapterLogger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void scanUpload_truncatesLoggedScannerErrorBody() {
        HttpClient httpClient = new ThrowingHttpClient("sensitive-body-".repeat(100));
        SkillScannerService service = new SkillScannerService(
                httpClient,
                "http://scanner.test",
                "/scan-upload",
                "/health"
        );
        attachAppender(serviceLogger);

        assertThatThrownBy(() -> service.scanUpload(Path.of("/tmp/demo.zip"), ScanOptions.disabled()))
                .isInstanceOf(HttpClientException.class);

        List<String> messages = loggedMessages();
        assertThat(messages).anyMatch(message -> message.contains("Scanner API error: status=500"));
        assertThat(messages).noneMatch(message -> message.contains("sensitive-body-".repeat(20)));
    }

    @Test
    void scan_logsSummaryWithoutDumpingFindingDetailsAtInfo() {
        StubSkillScannerService service = new StubSkillScannerService();
        service.directoryResponse = new SkillScannerApiResponse(
                "scan-9",
                "skill",
                false,
                "HIGH",
                1,
                List.of(new SkillScannerApiResponse.Finding(
                        "ID-1",
                        "RULE-1",
                        "HIGH",
                        "code-execution",
                        "Danger title",
                        "Very sensitive description",
                        "src/main.py",
                        7,
                        "eval(secret)",
                        "Use safe api",
                        "static",
                        Map.of("token", "secret")
                )),
                1.0,
                "2026-03-23T00:00:00"
        );
        SkillScannerAdapter adapter = new SkillScannerAdapter(service, "local", ScanOptions.disabled());
        attachAppender(adapterLogger);

        adapter.scan(new SecurityScanRequest("task-1", 1L, "/tmp/skill", Map.of()));

        List<String> messages = loggedMessages();
        assertThat(messages).anyMatch(message -> message.contains("Scanner API raw response"));
        assertThat(messages).noneMatch(message -> message.contains("Very sensitive description"));
        assertThat(messages).noneMatch(message -> message.contains("eval(secret)"));
        assertThat(messages).noneMatch(message -> message.contains("Mapped finding:"));
    }

    private void attachAppender(Logger logger) {
        logger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    private List<String> loggedMessages() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static final class ThrowingHttpClient implements HttpClient {
        private final String body;

        private ThrowingHttpClient(String body) {
            this.body = body;
        }

        @Override
        public <T> T get(String uri, Class<T> responseType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T post(String uri, Object body, Class<T> responseType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T postMultipart(String uri, MultiValueMap<String, Object> parts, Class<T> responseType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T postMultipart(String uri,
                                   MultiValueMap<String, Object> parts,
                                   HttpHeaders headers,
                                   Class<T> responseType) {
            throw new HttpClientException(500, body);
        }

        @Override
        public boolean isHealthy(String healthUri) {
            return false;
        }
    }

    private static final class StubSkillScannerService extends SkillScannerService {
        private SkillScannerApiResponse directoryResponse;

        private StubSkillScannerService() {
            super(new NoOpHttpClient(), "http://scanner.test", "/scan-upload", "/health");
        }

        @Override
        public SkillScannerApiResponse scanDirectory(String skillDirectory, ScanOptions options) {
            return directoryResponse;
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
        public <T> T postMultipart(String uri, MultiValueMap<String, Object> parts, Class<T> responseType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T postMultipart(String uri,
                                   MultiValueMap<String, Object> parts,
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
