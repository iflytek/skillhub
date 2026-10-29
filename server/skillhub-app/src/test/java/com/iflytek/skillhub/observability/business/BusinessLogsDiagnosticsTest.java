package com.iflytek.skillhub.observability.business;

import com.iflytek.skillhub.domain.event.BusinessEventSnapshot;
import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.MapPropertySource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/** Load the shipped logging config, including JUL-to-SLF4J routing used by Boot. */
@ExtendWith(OutputCaptureExtension.class)
class BusinessLogsDiagnosticsTest {
    private static final String CREDENTIAL = "SYNTHETIC-1009-CREDENTIAL";
    private LoggingSystem logging;

    private void configureShippedLogging(String format) {
        logging = LoggingSystem.get(getClass().getClassLoader());
        logging.beforeInitialize();
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("diagnostic-test",
                java.util.Map.of("skillhub.observability.log-format", format)));
        logging.initialize(new LoggingInitializationContext(environment), "classpath:logback-spring.xml", null);
        String processor = "io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor";
        org.slf4j.LoggerFactory.getLogger(processor);
        assertThat(logging.getLoggerConfiguration(processor).getEffectiveLevel())
                .isEqualTo(org.springframework.boot.logging.LogLevel.OFF);
    }

    @ParameterizedTest
    @ValueSource(strings = {"text", "json"})
    void businessLogsDisabledKeepsExistingTraceExportFailureDiagnostics(String format, CapturedOutput output)
            throws Exception {
        configureShippedLogging(format);
        try (var receiver = new UnauthorizedReceiver("/v1/traces")) {
            exportSpanFailure(receiver.endpoint());
            assertThat(receiver.requests.get()).isEqualTo(1);
            org.awaitility.Awaitility.await().untilAsserted(() -> assertThat(output.getAll())
                    .contains("Failed to export spans", "401", "trace-receiver-rejected"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"text", "json"})
    void businessLogsEnabledKeepsTraceDiagnosticsAndHidesBusinessReceiverResponse(String format,
                                                                                 CapturedOutput output) throws Exception {
        configureShippedLogging(format);
        try (var traces = new UnauthorizedReceiver("/v1/traces");
             var logs = new UnauthorizedReceiver("/v1/logs")) {
            exportFailure(logs.endpoint());
            exportSpanFailure(traces.endpoint());
            assertThat(logs.requests.get()).isEqualTo(1);
            assertThat(traces.requests.get()).isEqualTo(1);
            org.awaitility.Awaitility.await().untilAsserted(() -> assertThat(output.getAll())
                    .contains("Business Logs telemetry failed", "Failed to export spans"));
            assertThat(output.getAll()).doesNotContain(CREDENTIAL, "Failed to export logs");
        }
    }

    private static void exportSpanFailure(String endpoint) {
        var exporter = io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter.builder()
                .setEndpoint(endpoint).setTimeout(java.time.Duration.ofSeconds(2)).build();
        var provider = io.opentelemetry.sdk.trace.SdkTracerProvider.builder()
                .addSpanProcessor(io.opentelemetry.sdk.trace.export.SimpleSpanProcessor.create(exporter)).build();
        try {
            provider.get("trace-diagnostics-test").spanBuilder("trace-401").startSpan().end();
            assertThat(provider.forceFlush().join(5, TimeUnit.SECONDS).isSuccess()).isFalse();
        } finally { provider.shutdown().join(5, TimeUnit.SECONDS); }
    }

    /** Receiver that rejects with 401 and echoes a marker (the synthetic credential for /v1/logs). */
    private static final class UnauthorizedReceiver implements AutoCloseable {
        private final HttpServer server;
        private final String path;
        final java.util.concurrent.atomic.AtomicInteger requests = new java.util.concurrent.atomic.AtomicInteger();

        UnauthorizedReceiver(String path) throws java.io.IOException {
            this.path = path;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(path, exchange -> {
                exchange.getRequestBody().readAllBytes();
                requests.incrementAndGet();
                String echoed = path.endsWith("/logs") ? CREDENTIAL : "trace-receiver-rejected";
                byte[] text = ("auth failed for Bearer " + echoed).getBytes(StandardCharsets.UTF_8);
                byte[] body = new byte[text.length + 4];
                body[0] = 0x08; body[1] = 0x10; body[2] = 0x12; body[3] = (byte) text.length;
                System.arraycopy(text, 0, body, 4, text.length);
                exchange.getResponseHeaders().add("Content-Type", "application/x-protobuf");
                exchange.sendResponseHeaders(401, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
        }

        String endpoint() { return "http://127.0.0.1:" + server.getAddress().getPort() + path; }

        @Override public void close() { server.stop(0); }
    }

    @AfterEach
    void cleanupLogging() {
        if (logging != null) {
            logging.cleanUp();
            logging.initialize(new LoggingInitializationContext(new StandardEnvironment()), "classpath:logback-test.xml", null);
            logging.cleanUp();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"text", "json"})
    void receiverStatusEchoingAuthorizationCannotLeakToAnyConsoleLog(String format, CapturedOutput output) throws Exception {
        configureShippedLogging(format);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/logs", exchange -> {
            exchange.getRequestBody().readAllBytes();
            String message = "auth failed for " + exchange.getRequestHeaders().getFirst("Authorization");
            byte[] text = message.getBytes(StandardCharsets.UTF_8);
            // google.rpc.Status { code: 16, message: <echoed transport header> }
            byte[] body = new byte[text.length + 4];
            body[0] = 0x08; body[1] = 0x10; body[2] = 0x12; body[3] = (byte) text.length;
            System.arraycopy(text, 0, body, 4, text.length);
            exchange.getResponseHeaders().add("Content-Type", "application/x-protobuf");
            exchange.sendResponseHeaders(401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            exportFailure("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/logs");
            org.awaitility.Awaitility.await().untilAsserted(() -> assertThat(output.getAll()).contains("Business Logs telemetry failed"));
            assertThat(output.getAll()).contains("Business Logs telemetry failed")
                    .doesNotContain(CREDENTIAL, "auth failed for", "Failed to export logs");
        } finally { server.stop(0); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"text", "json"})
    void transportExceptionEchoingSensitiveTextCannotLeakToAnyConsoleLog(String format, CapturedOutput output) throws Exception {
        configureShippedLogging(format);
        // OkHttp reports an invalid status line verbatim in ProtocolException (transport onError).
        try (var receiver = new BusinessLogsTestReceiver(
                "HTTP/1.1 " + CREDENTIAL + "\r\nConnection: close\r\n\r\n")) {
            exportFailure(receiver.endpoint());
            assertThat(receiver.requests.get()).isEqualTo(1);
            org.awaitility.Awaitility.await().untilAsserted(() -> assertThat(output.getAll()).contains("Business Logs telemetry failed"));
            assertThat(output.getAll()).contains("Business Logs telemetry failed")
                    .doesNotContain(CREDENTIAL, "Unexpected status line", "Failed to export logs");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"text", "json"})
    void malformedChunkedUnauthorizedCannotPrintUncaughtExceptionAndLaterExportRecovers(
            String format, CapturedOutput output) throws Exception {
        configureShippedLogging(format);
        try (var receiver = new BusinessLogsTestReceiver(
                "HTTP/1.1 401 Unauthorized\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n"
                        + "1 Bearer " + CREDENTIAL + "\r\nx\r\n0\r\n\r\n")) {
            var previousHandler = Thread.getDefaultUncaughtExceptionHandler();
            var ownedGroups = new java.util.HashSet<ThreadGroup>();
            new ApplicationContextRunner().withUserConfiguration(BusinessLogsConfiguration.class)
                    .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
                    .withPropertyValues("skillhub.observability.business-logs.enabled=true",
                            "skillhub.observability.business-logs.endpoint=" + receiver.endpoint(),
                            "skillhub.observability.business-logs.headers=Authorization=Bearer%20" + CREDENTIAL,
                            "skillhub.observability.business-logs.retry-max-attempts=1",
                            "skillhub.observability.business-logs.timeout=100ms",
                            "skillhub.observability.business-logs.retry-initial-backoff=100ms",
                            "skillhub.observability.business-logs.retry-max-backoff=100ms",
                            "skillhub.observability.business-logs.max-queue-size=1",
                            "skillhub.observability.business-logs.max-export-batch-size=1")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        var snapshot = new BusinessEventSnapshot(7L, SkillVisibility.PUBLIC, "reviewer", "user", "review", Instant.now());
                        var event = new SkillPublishedEvent(1L, 2L, "reviewer", snapshot);
                        var pipeline = context.getBean(BusinessLogsPipeline.class);
                        var metrics = context.getBean(BusinessLogsMetrics.class);
                        context.publishEvent(event);
                        pipeline.forceFlush().join(5, TimeUnit.SECONDS);
                        org.awaitility.Awaitility.await().untilAsserted(() -> assertThat(metrics.exportFailures.count()).isEqualTo(1));
                        context.publishEvent(event);
                        pipeline.forceFlush().join(5, TimeUnit.SECONDS);
                        org.awaitility.Awaitility.await().untilAsserted(() -> assertThat(metrics.exported.count()).isEqualTo(1));
                        assertThat(metrics.enqueued.count()).isEqualTo(2);
                        assertThat(metrics.queueFull.count()).isZero();
                        assertThat(metrics.exportFailures.count()).isEqualTo(1);
                        assertThat(receiver.requests.get()).isEqualTo(2);
                        assertThat(metrics.errors.count()).isEqualTo(1);
                        Thread.getAllStackTraces().keySet().stream()
                                .filter(thread -> thread.getName().startsWith("okhttp-dispatch-"))
                                .map(Thread::getThreadGroup)
                                .filter(group -> group instanceof BusinessLogsExporterBoundary).forEach(ownedGroups::add);
                        assertThat(ownedGroups).hasSize(1);
                        assertThat(Thread.getDefaultUncaughtExceptionHandler()).isSameAs(previousHandler);
                    });
            org.awaitility.Awaitility.await().untilAsserted(() -> assertThat(Thread.getAllStackTraces().keySet())
                    .noneMatch(thread -> ownedGroups.contains(thread.getThreadGroup()) && thread.getName().startsWith("okhttp-dispatch-")));
            assertThat(Thread.getDefaultUncaughtExceptionHandler()).isSameAs(previousHandler);
            org.awaitility.Awaitility.await().untilAsserted(() -> assertThat(output.getAll()).contains("Business Logs telemetry failed"));
            // OutputCapture covers System.out/System.err, including the shipped text/async JSON appenders.
            assertThat(output.getOut()).doesNotContain(CREDENTIAL, "Exception in thread", "ProtocolException", "IllegalStateException");
            assertThat(output.getErr()).doesNotContain(CREDENTIAL, "Exception in thread", "ProtocolException", "IllegalStateException");
        }
    }

    private static void exportFailure(String endpoint) {
        new ApplicationContextRunner().withUserConfiguration(BusinessLogsConfiguration.class)
                .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues("skillhub.observability.business-logs.enabled=true",
                        "skillhub.observability.business-logs.endpoint=" + endpoint,
                        "skillhub.observability.business-logs.headers=Authorization=Bearer%20" + CREDENTIAL,
                        "skillhub.observability.business-logs.retry-max-attempts=1",
                        "skillhub.observability.business-logs.timeout=500ms")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var snapshot = new BusinessEventSnapshot(7L, SkillVisibility.PUBLIC, "reviewer", "user", "review", Instant.now());
                    context.publishEvent(new SkillPublishedEvent(1L, 2L, "reviewer", snapshot));
                    context.getBean(BusinessLogsPipeline.class).forceFlush().join(5, TimeUnit.SECONDS);
                    var metrics = context.getBean(BusinessLogsMetrics.class);
                    assertThat(metrics.exportFailures.count()).isEqualTo(1);
                    assertThat(metrics.exported.count()).isZero();
                });
    }
}
