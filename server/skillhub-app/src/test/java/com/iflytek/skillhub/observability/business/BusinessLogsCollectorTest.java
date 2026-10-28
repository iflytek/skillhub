package com.iflytek.skillhub.observability.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.event.*;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.observability.tracing.SkillHubTracingConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.common.AttributeKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;

/** Actual production configuration sends protobuf over HTTP to a pinned real Collector/file sink. */
@Testcontainers
class BusinessLogsCollectorTest {
    @Container
    static final GenericContainer<?> COLLECTOR = new GenericContainer<>("otel/opentelemetry-collector-contrib:0.111.0")
            .withExposedPorts(4318)
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("../../docs/examples/business-event-logs/collector.yaml").toAbsolutePath()),
                    "/etc/otelcol-contrib/config.yaml")
            .withCreateContainerCmdModifier(cmd -> {
                cmd.withName("skillhub-1009-collector-integration");
                cmd.withUser("0:0");
                cmd.getHostConfig().withMemory(128L * 1024 * 1024).withNanoCPUs(500_000_000L);
            })
            .waitingFor(Wait.forLogMessage(".*Everything is ready.*", 1));

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({SkillHubTracingConfiguration.class, BusinessLogsConfiguration.class})
    static class Application {}

    @Test
    void realCollectorReceivesNineFactsInAllThreeTraceModesAndResourceLimitsAreExplicit() throws Exception {
        var hostConfig = COLLECTOR.getCurrentContainerInfo().getHostConfig();
        assertThat(hostConfig.getMemory()).isEqualTo(128L * 1024 * 1024);
        assertThat(hostConfig.getNanoCPUs()).isEqualTo(500_000_000L);
        var snapshot = new BusinessEventSnapshot(7L, SkillVisibility.PRIVATE, "reviewer", "user", "review", Instant.now());
        var events = List.<SkillBusinessEvent>of(
                new SkillPublishedEvent(1L, 2L, "reviewer", snapshot),
                new SkillDownloadedEvent(1L, 2L, new BusinessEventSnapshot(7L, SkillVisibility.PUBLIC, null, "user", "download", snapshot.occurredAt())),
                new ReviewApprovedEvent(3L, 1L, 2L, "reviewer", "private-owner", snapshot),
                new ReviewRejectedEvent(3L, 1L, 2L, "reviewer", "private-owner", "SECRET-CONTENT-URL-TOKEN", snapshot),
                new PromotionApprovedEvent(4L, 1L, "reviewer", "private-owner", snapshot),
                new PromotionRejectedEvent(4L, 1L, "reviewer", "private-owner", "SECRET-CONTENT-URL-TOKEN", snapshot),
                new SkillStatusChangedEvent(1L, SkillStatus.ACTIVE, SkillStatus.ARCHIVED, snapshot),
                new SkillVisibilityChangedEvent(1L, SkillVisibility.PUBLIC, SkillVisibility.PRIVATE, snapshot),
                new SkillVersionYankedEvent(1L, 2L, "reviewer", true, snapshot));
        for (String mode : List.of("none", "otel-sdk", "external-agent")) {
            new ApplicationContextRunner().withUserConfiguration(Application.class)
                    .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
                    .withPropertyValues("spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none",
                            "skillhub.observability.tracing-mode=" + mode,
                            "management.tracing.sampling.probability=0",
                            "skillhub.observability.service-version=collector-test",
                            "skillhub.observability.service-environment=test",
                            "skillhub.observability.business-logs.enabled=true",
                            "skillhub.observability.business-logs.endpoint=http://" + COLLECTOR.getHost() + ":" + COLLECTOR.getMappedPort(4318) + "/v1/logs")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        events.forEach(context::publishEvent);
                        var result = context.getBean(BusinessLogsPipeline.class).forceFlush();
                        result.join(10, TimeUnit.SECONDS);
                        assertThat(result.isSuccess()).isTrue();
                        var registry = context.getBean(SimpleMeterRegistry.class);
                        assertThat(registry.get("skillhub.business.logs.exported").counter().count()).isEqualTo(9);
                    });
        }
        String raw = awaitFile(27);
        assertThat(raw).doesNotContain("SECRET-CONTENT-URL-TOKEN", "private-owner", "request.id", "authorization");
        List<JsonNode> records = records(raw);
        assertThat(records).hasSize(27);
        var names = new HashSet<String>();
        var uids = new HashSet<String>();
        for (JsonNode record : records) {
            var attrs = attributes(record);
            names.add(attrs.get("skillhub.event.name").asText());
            assertThat(uids.add(attrs.get("log.record.uid").asText())).isTrue();
            assertThat(attrs.size()).isLessThanOrEqualTo(16);
            assertThat(attrs.get("skillhub.namespace.id").asText()).isEqualTo("7");
            if (attrs.get("skillhub.event.name").asText().equals("skill.download.prepared")) { assertThat(attrs).doesNotContainKey("user.id"); }
        }
        assertThat(names).containsExactlyInAnyOrderElementsOf(BusinessEventMapper.EVENT_NAMES);
        String evidence = System.getProperty("skillhub.test.evidence-dir");
        if (evidence != null) { Files.writeString(Path.of(evidence, "collector-received.jsonl"), raw); }
        System.out.println("BUSINESS_LOGS real Collector 0.111.0: queried 27 records / 9 event names / 27 unique uids; modes none,otel-sdk(sample=0),external-agent; 128 MiB, 0.5 CPU");
        String endpoint = "http://" + COLLECTOR.getHost() + ":" + COLLECTOR.getMappedPort(4318) + "/v1/logs";
        COLLECTOR.getDockerClient().pauseContainerCmd(COLLECTOR.getContainerId()).exec();
        try {
            outageRunner(endpoint).run(context -> {
                assertThat(context).hasNotFailed();
                long start = System.nanoTime();
                for (int i = 0; i < 1000; i++) { context.publishEvent(events.getFirst()); }
                long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                assertThat(elapsed).isLessThan(1000);
                context.getBean(BusinessLogsPipeline.class).forceFlush().join(5, TimeUnit.SECONDS);
                var registry = context.getBean(SimpleMeterRegistry.class);
                double dropped = registry.get("skillhub.business.logs.dropped").tag("reason", "queue_full").counter().count();
                assertThat(dropped).isGreaterThanOrEqualTo(998);
                assertThat(registry.get("skillhub.business.logs.export.failures").counter().count()).isPositive();
                System.out.printf("BUSINESS_LOGS paused real Collector: 1000 caller events in %d ms, drops=%.0f; timeout failures counted%n", elapsed, dropped);
            });
        } finally { COLLECTOR.getDockerClient().unpauseContainerCmd(COLLECTOR.getContainerId()).exec(); }
        COLLECTOR.stop();
        outageRunner(endpoint).run(context -> {
            assertThat(context).hasNotFailed();
            long start = System.nanoTime();
            context.publishEvent(events.getFirst());
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertThat(elapsed).isLessThan(1000);
            context.getBean(BusinessLogsPipeline.class).forceFlush().join(5, TimeUnit.SECONDS);
            assertThat(context.getBean(SimpleMeterRegistry.class).get("skillhub.business.logs.export.failures").counter().count()).isEqualTo(1);
            System.out.printf("BUSINESS_LOGS stopped real Collector: caller=%d ms; failure counted, no business exception%n", elapsed);
        });
    }

    private static ApplicationContextRunner outageRunner(String endpoint) {
        return new ApplicationContextRunner().withUserConfiguration(BusinessLogsConfiguration.class)
                .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues("skillhub.observability.business-logs.enabled=true",
                        "skillhub.observability.business-logs.endpoint=" + endpoint,
                        "skillhub.observability.business-logs.max-queue-size=2",
                        "skillhub.observability.business-logs.max-export-batch-size=1",
                        "skillhub.observability.business-logs.timeout=500ms",
                        "skillhub.observability.business-logs.retry-max-attempts=1");
    }

    private static String awaitFile(int expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        String raw = "";
        Exception lastError = null;
        do {
            try {
                raw = COLLECTOR.copyFileFromContainer("/business-logs.json", stream -> new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                if (records(raw).size() >= expected) { return raw; }
            } catch (Exception ex) { lastError = ex; }
            TimeUnit.MILLISECONDS.sleep(100);
        } while (System.nanoTime() < deadline);
        String evidence = System.getProperty("skillhub.test.evidence-dir");
        if (evidence != null) {
            Files.writeString(Path.of(evidence, "collector-debug.jsonl"), raw);
            Files.writeString(Path.of(evidence, "collector-debug.log"), COLLECTOR.getLogs());
        }
        throw new AssertionError("Collector file did not contain " + expected + " records; raw characters=" + raw.length(), lastError);
    }

    static List<JsonNode> records(String raw) throws Exception {
        List<JsonNode> result = new ArrayList<>();
        var mapper = new ObjectMapper();
        for (String line : raw.lines().filter(s -> !s.isBlank()).toList()) {
            JsonNode root = mapper.readTree(line);
            for (JsonNode resource : root.path("resourceLogs")) {
                for (JsonNode scope : resource.path("scopeLogs")) {
                    for (JsonNode record : scope.path("logRecords")) { result.add(record); }
                }
            }
        }
        return result;
    }

    static Map<String, JsonNode> attributes(JsonNode record) {
        var result = new java.util.HashMap<String, JsonNode>();
        for (JsonNode attr : record.path("attributes")) { result.put(attr.path("key").asText(), attr.path("value").elements().next()); }
        return result;
    }
}
