package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.support.PostgresTestImage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.githubsync.GithubOfficialDiscoveryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Real Go OS processes and Java HTTP/PostgreSQL; GitHub responses are controlled fixtures. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@Import(GithubOfficialDiscoveryProcessInteropTest.RedisFixtureConfig.class)
@TestPropertySource(properties = {
        "skillhub.github-sync.service-tokens=fixture-official-process-service-token-32-chars",
        "skillhub.builtin-skills.enabled=false"
})
class GithubOfficialDiscoveryProcessInteropTest {
    private static final String SERVICE_TOKEN = "fixture-official-process-service-token-32-chars";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PostgresTestImage.name());
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @TestConfiguration
    static class RedisFixtureConfig {
        @Bean
        RedisProperties redisProperties() {
            RedisProperties properties = new RedisProperties();
            properties.setHost(REDIS.getHost());
            properties.setPort(REDIS.getMappedPort(6379));
            return properties;
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired GithubOfficialDiscoveryRepository scheduler;
    @LocalServerPort int port;
    @TempDir Path temporary;

    @Test
    void killedGoProcessIsReplacedWithoutReplayingAcknowledgedPage() throws Exception {
        jdbc.update("UPDATE github_deep_discovery_config SET enabled=TRUE WHERE id");
        jdbc.update("""
                UPDATE github_sync_admission_policy
                SET global_high=10000,global_low=5000,namespace_high=10000,namespace_low=5000 WHERE id
                """);
        assertThat(scheduler.enqueueDue(Instant.now(), 1)).isEqualTo(1);
        UUID taskId = jdbc.queryForObject("""
                SELECT id FROM github_sync_task WHERE official_query_id IS NOT NULL AND status='QUEUED'
                """, UUID.class);
        Path executable = temporary.resolve("official-worker.test");
        Path buildLog = temporary.resolve("build.log");
        Process build = new ProcessBuilder("go", "test", "-c", "-o", executable.toString(), "./internal/worker")
                .directory(findGoModule().toFile()).redirectErrorStream(true).redirectOutput(buildLog.toFile()).start();
        awaitExit(build, buildLog, 120);

        Path barrier = temporary.resolve("page-two.ready");
        Path firstLog = temporary.resolve("first.log");
        Process first = process(executable, taskId, "before-kill", barrier, firstLog).start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            while (!Files.exists(barrier) && first.isAlive() && System.nanoTime() < deadline) {
                Thread.sleep(25);
            }
            assertThat(Files.exists(barrier)).as("first process: %s", Files.readString(firstLog)).isTrue();
            String saved = jdbc.queryForObject("SELECT discovery_checkpoint FROM github_sync_task WHERE id=?",
                    String.class, taskId);
            assertThat(mapper.readTree(saved).path("nextPage").asInt()).isEqualTo(2);
            assertThat(mapper.readTree(saved).path("coverage").asText()).isEqualTo("IN_PROGRESS");
            long firstGeneration = jdbc.queryForObject("SELECT lease_generation FROM github_sync_task WHERE id=?",
                    Long.class, taskId);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM github_discovery_candidate WHERE repository_id=987654901",
                    Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM github_discovery_candidate WHERE repository_id=987654902",
                    Integer.class)).isZero();

            first.destroyForcibly();
            assertThat(first.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(first.exitValue()).isNotZero();
            jdbc.update("""
                    UPDATE github_sync_task SET lease_expires_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE id=?
                    """, taskId);
            // This crash-recovery fixture advances pacing instead of waiting 200 seconds.
            // The independent budget integration suite verifies the persistent delay itself.
            jdbc.update("UPDATE github_official_discovery_budget SET next_lease_eligible_at=clock_timestamp()-INTERVAL '1 second'");
            assertThat(jdbc.queryForObject("SELECT discovery_checkpoint FROM github_sync_task WHERE id=?",
                    String.class, taskId)).isEqualTo(saved);

            Path resumedLog = temporary.resolve("resumed.log");
            Process resumed = process(executable, taskId, "resume", barrier, resumedLog).start();
            awaitExit(resumed, resumedLog, 60);
            assertThat(jdbc.queryForObject("SELECT lease_generation FROM github_sync_task WHERE id=?",
                    Long.class, taskId)).isEqualTo(firstGeneration + 1);
            assertThat(jdbc.queryForObject("SELECT status FROM github_sync_task WHERE id=?",
                    String.class, taskId)).isEqualTo("ACQUISITION_COMPLETE");
            String completed = jdbc.queryForObject("SELECT discovery_checkpoint FROM github_sync_task WHERE id=?",
                    String.class, taskId);
            assertThat(mapper.readTree(completed).path("nextPage").asInt()).isEqualTo(2);
            assertThat(mapper.readTree(completed).path("coverage").asText()).isEqualTo("COMPLETE");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM github_discovery_candidate WHERE repository_id IN (987654901,987654902)
                    """, Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM github_discovery_outcome_event
                    WHERE event_type='CANDIDATE_CREATED' AND repository_id IN (987654901,987654902)
                    """, Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM github_discovery_batch WHERE task_id=?",
                    Integer.class, taskId)).isEqualTo(3);
        } finally {
            if (first.isAlive()) {
                first.destroyForcibly();
                first.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }

    private ProcessBuilder process(Path executable, UUID taskId, String phase, Path barrier, Path log) {
        ProcessBuilder builder = new ProcessBuilder(executable.toString(),
                "-test.run=^TestInteropOfficialDiscoveryProcess$", "-test.v", "-test.timeout=90s")
                .directory(findGoModule().toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().keySet().removeIf(key -> key.startsWith("SKILLHUB_")
                && (key.contains("LIVE") || key.contains("INTEROP")));
        builder.environment().put("SKILLHUB_INTEROP_OFFICIAL_PROCESS", phase);
        builder.environment().put("SKILLHUB_INTEROP_JAVA_URL", "http://127.0.0.1:" + port + "/internal/github-sync/v1");
        builder.environment().put("SKILLHUB_INTEROP_SERVICE_TOKEN", SERVICE_TOKEN);
        builder.environment().put("SKILLHUB_INTEROP_TASK_ID", taskId.toString());
        builder.environment().put("SKILLHUB_INTEROP_BARRIER", barrier.toString());
        return builder;
    }

    private static void awaitExit(Process process, Path log, int timeoutSeconds) throws Exception {
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
            throw new AssertionError("Go process timed out: " + Files.readString(log));
        }
        assertThat(process.exitValue()).as("Go process: %s", Files.readString(log)).isZero();
    }

    private static Path findGoModule() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("github-sync-go");
            if (Files.isRegularFile(candidate.resolve("go.mod"))) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate github-sync-go module");
    }
}
