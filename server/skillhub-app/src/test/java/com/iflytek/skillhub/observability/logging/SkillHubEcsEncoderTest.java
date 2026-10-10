package com.iflytek.skillhub.observability.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SkillHubEcsEncoderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LoggerContext loggerContext = new LoggerContext();
    private final SkillHubEcsEncoder encoder = new SkillHubEcsEncoder();

    @BeforeEach
    void setUp() {
        loggerContext.setMDCAdapter(new LogbackMDCAdapter());
        encoder.setContext(loggerContext);
        encoder.setServiceName("skillhub");
        encoder.setServiceVersion("test-sha");
        encoder.setServiceEnvironment("test");
        encoder.start();
    }

    @AfterEach
    void tearDown() {
        encoder.stop();
        loggerContext.stop();
    }

    @Test
    void shouldWriteEcsFieldsAndOnlyApprovedMdcValues() throws Exception {
        LoggingEvent event = event("hello");
        event.setMDCPropertyMap(Map.of(
                "requestId", "req-123",
                "traceId", "trace-123",
                "spanId", "span-123",
                "authorization", "must-not-leak",
                "userEmail", "must-not-leak"
        ));

        JsonNode json = encode(event);

        assertThat(json.path("log.level").asText()).isEqualTo("INFO");
        assertThat(json.path("log.logger").asText()).isEqualTo("test.logger");
        assertThat(json.path("message").asText()).isEqualTo("hello");
        assertThat(json.path("service.name").asText()).isEqualTo("skillhub");
        assertThat(json.path("service.version").asText()).isEqualTo("test-sha");
        assertThat(json.path("service.environment").asText()).isEqualTo("test");
        assertThat(json.path("request.id").asText()).isEqualTo("req-123");
        assertThat(json.path("trace.id").asText()).isEqualTo("trace-123");
        assertThat(json.path("span.id").asText()).isEqualTo("span-123");
        assertThat(json.has("authorization")).isFalse();
        assertThat(json.has("userEmail")).isFalse();
    }

    @Test
    void shouldPreferMicrometerTraceIdOverExternalAgentFallback() throws Exception {
        LoggingEvent event = event("trace precedence");
        event.setMDCPropertyMap(Map.of(
                "traceId", "micrometer-trace",
                "tid", "external-agent-trace"
        ));

        JsonNode json = encode(event);

        assertThat(json.path("trace.id").asText()).isEqualTo("micrometer-trace");
        assertThat(json.fieldNames()).toIterable()
                .filteredOn("trace.id"::equals)
                .hasSize(1);
    }

    @Test
    void shouldNormalizeExternalAgentTraceId() throws Exception {
        encoder.stop();
        encoder.setTracingMode("external-agent");
        encoder.start();
        LoggingEvent event = event("external trace");
        event.setMDCPropertyMap(Map.of("tid", "TID: external-agent-trace"));

        JsonNode json = encode(event);

        assertThat(json.path("trace.id").asText()).isEqualTo("external-agent-trace");
    }

    @Test
    void shouldNotWriteToolkitSentinelAsTraceId() throws Exception {
        encoder.stop();
        encoder.setTracingMode("external-agent");
        encoder.start();
        LoggingEvent event = event("no external agent");
        event.setMDCPropertyMap(Map.of("tid", "TID: N/A"));

        JsonNode json = encode(event);

        assertThat(json.has("trace.id")).isFalse();
    }

    @Test
    void shouldWriteStructuredExceptionFields() throws Exception {
        LoggingEvent event = event("failed");
        event.setThrowableProxy(new ThrowableProxy(new IllegalStateException("boom")));

        JsonNode json = encode(event);

        assertThat(json.path("error.type").asText())
                .isEqualTo(IllegalStateException.class.getName());
        assertThat(json.path("error.message").asText()).isEqualTo("boom");
        assertThat(json.path("error.stack_trace").asText())
                .contains("IllegalStateException: boom");
    }

    private LoggingEvent event(String message) {
        Logger logger = loggerContext.getLogger("test.logger");
        LoggingEvent event = new LoggingEvent(
                getClass().getName(),
                logger,
                Level.INFO,
                message,
                null,
                null
        );
        event.setThreadName("test-thread");
        event.setTimeStamp(1_785_465_600_000L);
        return event;
    }

    private JsonNode encode(LoggingEvent event) throws Exception {
        return objectMapper.readTree(new String(encoder.encode(event), StandardCharsets.UTF_8));
    }
}
