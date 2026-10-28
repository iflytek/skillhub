package com.iflytek.skillhub.observability.business;

import com.iflytek.skillhub.domain.event.BusinessEventSnapshot;
import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.context.Context;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.sdk.common.export.RetryPolicy;
import io.opentelemetry.sdk.resources.Resource;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessLogsHttpTest {
    @Test
    void boundedRetryReusesIdenticalUidAndPayloadAndAuthenticationIsTransportOnly() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<byte[]> bodies = new CopyOnWriteArrayList<>();
        List<String> auth = new CopyOnWriteArrayList<>();
        var requests = new AtomicInteger();
        server.createContext("/v1/logs", exchange -> {
            bodies.add(exchange.getRequestBody().readAllBytes());
            auth.add(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(requests.incrementAndGet() == 1 ? 503 : 200, -1);
            exchange.close();
        });
        server.start();
        var properties = new BusinessLogsProperties();
        properties.setCompression("none");
        var metrics = new BusinessLogsMetrics(new SimpleMeterRegistry());
        var exporter = OtlpHttpLogRecordExporter.builder()
                .setEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/logs")
                .setTimeout(Duration.ofMillis(500)).addHeader("Authorization", "Bearer fake-deployment-secret")
                .setRetryPolicy(RetryPolicy.builder().setMaxAttempts(2).setInitialBackoff(Duration.ofMillis(10))
                        .setMaxBackoff(Duration.ofMillis(10)).build()).build();
        try (var pipeline = new BusinessLogsPipeline(properties, Resource.empty(), exporter, metrics)) {
            var snapshot = new BusinessEventSnapshot(7L, SkillVisibility.PUBLIC, "reviewer", "user", "review", Instant.now());
            var record = new BusinessEventMapper(properties).map(new SkillPublishedEvent(1L, 2L, "reviewer", snapshot)).orElseThrow();
            pipeline.emit(record, Context.root());
            pipeline.forceFlush().join(5, TimeUnit.SECONDS);
            assertThat(requests.get()).isEqualTo(2);
            assertThat(bodies.get(0)).isEqualTo(bodies.get(1));
            String payload = new String(bodies.getFirst(), java.nio.charset.StandardCharsets.ISO_8859_1);
            assertThat(payload).contains(record.uid()).doesNotContain("fake-deployment-secret", "Authorization");
            assertThat(auth).containsExactly("Bearer fake-deployment-secret", "Bearer fake-deployment-secret");
            assertThat(metrics.exported.count()).isEqualTo(1);
            assertThat(metrics.exportFailures.count()).isZero();
            System.out.println("BUSINESS_LOGS retry: 503 then 200, 2 identical payloads, 1 stable uid; transport-only auth");
        } finally { server.stop(0); }
    }

    @Test
    void twoHundredPartialSuccessIsCountedAsSdkSuccessWithoutWholeBatchReplay() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var requests = new AtomicInteger();
        server.createContext("/v1/logs", exchange -> {
            exchange.getRequestBody().readAllBytes();
            requests.incrementAndGet();
            // ExportLogsServiceResponse.partial_success { rejected_log_records: 1 }
            byte[] body = {0x0a, 0x02, 0x08, 0x01};
            exchange.getResponseHeaders().add("Content-Type", "application/x-protobuf");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        var metrics = new BusinessLogsMetrics(new SimpleMeterRegistry());
        var properties = new BusinessLogsProperties();
        var exporter = OtlpHttpLogRecordExporter.builder()
                .setEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/logs").build();
        try (var pipeline = new BusinessLogsPipeline(properties, Resource.empty(), exporter, metrics)) {
            var snapshot = new BusinessEventSnapshot(7L, SkillVisibility.PUBLIC, "reviewer", "user", "review", Instant.now());
            pipeline.emit(new BusinessEventMapper(properties).map(new SkillPublishedEvent(1L, 2L, "reviewer", snapshot)).orElseThrow(), Context.root());
            pipeline.forceFlush().join(5, TimeUnit.SECONDS);
            assertThat(requests.get()).isEqualTo(1);
            assertThat(metrics.exported.count()).isEqualTo(1);
            System.out.println("BUSINESS_LOGS partial-success: 200 rejected=1 body, SDK success=1, requests=1 (known 1.31 limitation)");
        } finally { server.stop(0); }
    }
}
