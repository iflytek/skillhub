package com.iflytek.skillhub.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.review.ReviewOriginType;
import com.iflytek.skillhub.domain.review.ReviewService;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.dto.GithubSearchRuleRequest;
import com.iflytek.skillhub.dto.GithubSearchSelectorRequest;
import com.iflytek.skillhub.dto.GithubCuratedOwnerRequest;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.githubsync.GithubCandidateTaskRepository;
import com.iflytek.skillhub.githubsync.GithubCredentialCipher;
import com.iflytek.skillhub.githubsync.GithubDiscoveryEntryRepository;
import com.iflytek.skillhub.githubsync.GithubSourceTaskRepository;
import com.iflytek.skillhub.githubsync.GithubPackageAdmissionResult;
import com.iflytek.skillhub.githubsync.GithubSyncTaskKind;
import com.iflytek.skillhub.githubsync.GithubSyncTaskRepository;
import com.iflytek.skillhub.githubsync.GithubSyncIngestionRepository;
import com.iflytek.skillhub.githubsync.GithubSyncPublicationRepository;
import com.iflytek.skillhub.githubsync.GithubSystemCredential;
import com.iflytek.skillhub.githubsync.GithubSystemCredentialRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncIngestionRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncPublicationRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncSchedulePolicyRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncSourceCheckpointRepository;
import com.iflytek.skillhub.metrics.GithubSyncStagingCapacityMetrics;
import com.iflytek.skillhub.repository.GithubSyncWorkerPresenceRepository;
import com.iflytek.skillhub.repository.GithubSyncManagementQueryRepository;
import com.iflytek.skillhub.service.GithubDiscoveryAdminAppService;
import com.iflytek.skillhub.service.GithubSearchPreviewAppService;
import com.iflytek.skillhub.storage.ObjectStorageService;
import com.iflytek.skillhub.storage.StoragePrefixUsage;
import com.iflytek.skillhub.task.GithubSyncIngestionTask;
import com.iflytek.skillhub.task.GithubSyncPublicationTask;
import com.iflytek.skillhub.task.ScanTaskOutboxDispatcher;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileSystemUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Opt-in wire and PostgreSQL check using the ZIP produced by the real Go acquisition test. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@EnabledIfEnvironmentVariable(named = "SKILLHUB_GITHUB_SYNC_ARCHIVE_FIXTURE", matches = ".+")
@Import(GithubSyncHttpIngestionInteropTest.RedisFixtureConfig.class)
@TestPropertySource(properties = {
        "skillhub.github-sync.service-tokens=fixture-service-token-for-http-interop-32-chars",
        "skillhub.builtin-skills.enabled=false",
        "skillhub.security.scanner.enabled=true",
        "skillhub.publish.max-package-size=65536",
        "skillhub.github-sync.staging-capacity-limit-bytes=1024",
        "skillhub.github-sync.ingestion-validation-interval-ms=3600000",
        "skillhub.github-sync.publication-interval-ms=3600000",
        "skillhub.security.outbox.dispatch-interval-ms=3600000"
})
class GithubSyncHttpIngestionInteropTest {
    private static final String COMMIT = "49f948faa9258a0c61caceaf225e179651397431";
    private static final String SKILL_PATH = "skills/.system/skill-creator";
    private static final String SERVICE_TOKEN = "fixture-service-token-for-http-interop-32-chars";
    private static final byte[] CREDENTIAL_KEY = new byte[32];
    static {
        new SecureRandom().nextBytes(CREDENTIAL_KEY);
    }
    private static final boolean S3_MODE = "s3".equalsIgnoreCase(
            System.getenv("SKILLHUB_GITHUB_SYNC_INTEROP_STORAGE_MODE"));
    private static final String MINIO_ACCESS_KEY = "interop" + UUID.randomUUID().toString().replace("-", "");
    private static final String MINIO_SECRET_KEY = UUID.randomUUID().toString().replace("-", "");
    private static final String S3_BUCKET = "skillhub-interop-" + UUID.randomUUID().toString().replace("-", "");
    private static final Path STORAGE_ROOT = Path.of(System.getProperty("java.io.tmpdir"),
            "skillhub-github-http-interop-" + UUID.randomUUID());
    private static final GenericContainer<?> MINIO = new GenericContainer<>("quay.io/minio/minio:latest")
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", MINIO_ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_SECRET_KEY)
            .withCommand("server", "/data")
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));
    private static String s3Endpoint;

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);
    private static HttpServer scanner;
    private static String scannerBaseUrl;
    private static final AtomicReference<String> scannerFailureMarker = new AtomicReference<>();

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

    /** Claims from another JVM, then exits without settling the validation lease. */
    public static final class IngestionClaimProcess {
        public static void main(String[] args) {
            DriverManagerDataSource source = new DriverManagerDataSource(
                    System.getenv("SKILLHUB_RESTART_JDBC_URL"),
                    System.getenv("SKILLHUB_RESTART_JDBC_USER"),
                    System.getenv("SKILLHUB_RESTART_JDBC_PASSWORD"));
            JdbcTemplate jdbc = new JdbcTemplate(source);
            var repository = new JdbcGithubSyncIngestionRepository(jdbc,
                    new JdbcGithubSyncSourceCheckpointRepository(jdbc),
                    new JdbcGithubSyncSchedulePolicyRepository(jdbc), Duration.ofHours(24));
            var claim = repository.claim(UUID.randomUUID(), Instant.now(),
                    Instant.now().plusSeconds(2)).orElseThrow();
            if (!claim.id().equals(UUID.fromString(args[0]))) {
                throw new IllegalStateException("Claimed a different ingestion");
            }
            System.out.println("CLAIMED");
        }
    }

    /** Claims publication work from a separate JVM, then exits before committing a Skill. */
    public static final class PublicationClaimProcess {
        public static void main(String[] args) {
            DriverManagerDataSource source = new DriverManagerDataSource(
                    System.getenv("SKILLHUB_RESTART_JDBC_URL"),
                    System.getenv("SKILLHUB_RESTART_JDBC_USER"),
                    System.getenv("SKILLHUB_RESTART_JDBC_PASSWORD"));
            var repository = new JdbcGithubSyncPublicationRepository(new JdbcTemplate(source));
            var claim = repository.claim(UUID.randomUUID(), Instant.now(),
                    Instant.now().plusSeconds(2)).orElseThrow();
            if (!claim.id().equals(UUID.fromString(args[0]))) {
                throw new IllegalStateException("Claimed a different publication");
            }
            System.out.println("CLAIMED");
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        if ("1".equals(System.getenv("SKILLHUB_GITHUB_SYNC_LIVE_ALL_PACKAGES_INTEROP"))
                || System.getenv("SKILLHUB_GITHUB_SYNC_INTEROP_IMAGE") != null) {
            registry.add("skillhub.publish.max-package-size", () -> 104857600);
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("skillhub.github-sync.credential-keys",
                () -> "v1=" + Base64.getEncoder().encodeToString(CREDENTIAL_KEY));
        registry.add("skillhub.github-sync.active-credential-key-version", () -> "v1");
        registry.add("skillhub.storage.local.base-path", STORAGE_ROOT::toString);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        scannerBaseUrl = System.getenv("SKILLHUB_GITHUB_SYNC_INTEROP_SCANNER_URL");
        boolean externalScanner = scannerBaseUrl != null && !scannerBaseUrl.isBlank();
        if (!externalScanner) {
            try {
                scanner = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot start scanner fixture", exception);
            }
            scanner.createContext("/scan", exchange -> {
                exchange.getRequestBody().readAllBytes();
                String failureMarker = scannerFailureMarker.get();
                byte[] response = (failureMarker == null ? """
                        {"scan_id":"interop-safe","skill_name":"skill-creator","is_safe":true,
                         "max_severity":"LOW","findings_count":0,"findings":[],
                         "scan_duration_seconds":0.01}
                        """ : "{\"detail\":\"" + failureMarker + "\"}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(failureMarker == null ? 200 : 400, response.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(response);
                }
            });
            scanner.start();
            scannerBaseUrl = "http://127.0.0.1:" + scanner.getAddress().getPort();
        }
        registry.add("skillhub.security.scanner.base-url", () -> scannerBaseUrl);
        registry.add("skillhub.security.scanner.mode", () -> externalScanner ? "upload" : "local");
        if (S3_MODE) {
            MINIO.start();
            s3Endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
            registry.add("skillhub.storage.provider", () -> "s3");
            registry.add("skillhub.storage.s3.endpoint", () -> s3Endpoint);
            registry.add("skillhub.storage.s3.bucket", () -> S3_BUCKET);
            registry.add("skillhub.storage.s3.access-key", () -> MINIO_ACCESS_KEY);
            registry.add("skillhub.storage.s3.secret-key", () -> MINIO_SECRET_KEY);
            registry.add("skillhub.storage.s3.region", () -> "us-east-1");
            registry.add("skillhub.storage.s3.auto-create-bucket", () -> true);
            registry.add("skillhub.storage.s3.force-path-style", () -> true);
        }
    }

    @AfterAll
    static void cleanStorage() throws IOException {
        if (scanner != null) {
            scanner.stop(0);
        }
        FileSystemUtils.deleteRecursively(STORAGE_ROOT);
        if (MINIO.isRunning()) {
            MINIO.stop();
        }
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private GithubSyncIngestionTask ingestionTask;
    @Autowired private GithubSyncPublicationTask publicationTask;
    @Autowired private ObjectStorageService storage;
    @Autowired private GithubSyncStagingCapacityMetrics capacityMetrics;
    @Autowired private GithubSearchPreviewAppService searchPreviews;
    @Autowired private GithubDiscoveryAdminAppService discoveryAdmin;
    @Autowired private GithubDiscoveryEntryRepository discoveryEntries;
    @Autowired private GithubSyncWorkerPresenceRepository workerPresence;
    @Autowired private GithubSyncManagementQueryRepository managementQueries;
    @Autowired private GithubCredentialCipher credentialCipher;
    @Autowired private GithubSystemCredentialRepository systemCredentials;
    @Autowired private MeterRegistry meterRegistry;
    @Autowired private ReviewTaskRepository reviews;
    @Autowired private ReviewService reviewService;
    @Autowired private ScanTaskOutboxDispatcher scanTaskOutboxDispatcher;
    @Autowired private GithubSyncPublicationRepository publications;
    @Autowired private GithubSyncIngestionRepository ingestions;
    @Autowired private GithubSourceTaskRepository sourceTasks;
    @Autowired private GithubSyncTaskRepository syncTasks;
    @Autowired private GithubCandidateTaskRepository candidateTasks;
    @LocalServerPort private int port;

    @Test
    @EnabledIfEnvironmentVariable(named = "SKILLHUB_GITHUB_SYNC_OPENAPI_OUTPUT", matches = ".+")
    void exportedOpenApiIncludesLiveAdmissionCounts() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/v3/api-docs")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("globalUnresolvedCount", "platformUnresolvedCount",
                "platformPendingCandidateCount", "platformPaused", "failedPackages");
        Files.writeString(Path.of(System.getenv("SKILLHUB_GITHUB_SYNC_OPENAPI_OUTPUT")), response.body());
    }

    @Test
    void admissionPolicyHttpIncludesCurrentBacklogAndCandidateCount() throws Exception {
        long namespaceId = managementQueries.platformNamespaceId();
        var before = managementQueries.admissionSnapshot();
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        jdbc.update("INSERT INTO github_sync_run (id, namespace_id, kind) VALUES (?, ?, 'REPOSITORY_SYNC')",
                runId, namespaceId);
        jdbc.update("""
                INSERT INTO github_sync_task
                    (id, run_id, namespace_id, kind, mode, status, dedupe_key, snapshot_json,
                     lease_generation, lease_token_hash, lease_expires_at, leased_by)
                VALUES (?, ?, ?, 'REPOSITORY_SYNC', 'FULL', 'LEASED', ?,
                        jsonb_build_object('repositoryId', 987654334), 1, ?,
                        CURRENT_TIMESTAMP + INTERVAL '5 minutes', 'overview-test')
                """, taskId, runId, namespaceId, "repository:987654334", "a".repeat(64));
        UUID admissionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO github_sync_package_admission
                    (id, task_id, namespace_id, repository_id, commit_sha, skill_path,
                     lease_generation)
                VALUES (?, ?, ?, 987654334, ?, 'skills/overview-test', 1)
                """, admissionId, taskId, namespaceId, "b".repeat(40));
        long candidateId = jdbc.queryForObject("""
                INSERT INTO github_discovery_candidate
                    (namespace_id, repository_id, owner_login, repository_name, status,
                     next_validation_at)
                VALUES (?, 987654335, 'fixture', 'overview-candidate', 'PENDING',
                        CURRENT_TIMESTAMP + INTERVAL '1 day') RETURNING id
                """, Long.class, namespaceId);
        try {
            var admin = authentication(new UsernamePasswordAuthenticationToken(
                    new PlatformPrincipal("github-sync-publisher", "overview-admin", null,
                            null, null, Set.of("SUPER_ADMIN")), null,
                    List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
            String response = mockMvc.perform(get("/api/v1/admin/github-sync/admission-policy").with(admin))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var data = objectMapper.readTree(response).path("data");
            assertThat(data.path("globalUnresolvedCount").asInt())
                    .isEqualTo(before.globalUnresolvedCount() + 1);
            assertThat(data.path("platformUnresolvedCount").asInt())
                    .isEqualTo(before.platformUnresolvedCount() + 1);
            assertThat(data.path("platformPendingCandidateCount").asInt())
                    .isEqualTo(before.platformPendingCandidateCount() + 1);
        } finally {
            jdbc.update("UPDATE github_sync_package_admission SET status = 'RELEASED' WHERE id = ?",
                    admissionId);
            jdbc.update("""
                    UPDATE github_sync_task SET status = 'FAILED', lease_expires_at = NULL,
                        lease_token_hash = NULL, leased_by = NULL WHERE id = ?
                    """, taskId);
            jdbc.update("UPDATE github_discovery_candidate SET status = 'REJECTED' WHERE id = ?",
                    candidateId);
        }
    }

    @Test
    void goHeartbeatPersistsOnlineAndOfflineWorkerState() throws Exception {
        jdbc.update("DELETE FROM github_sync_worker_presence");
        assertThat(workerPresence.status().lastSeenAt()).isNull();
        assertThat(workerPresence.status().online()).isFalse();

        assertThat(runWorkerHeartbeat()).contains("PASS");
        assertThat(workerPresence.status().lastSeenAt()).isNotNull();
        assertThat(workerPresence.status().online()).isTrue();

        jdbc.update("""
                UPDATE github_sync_worker_presence
                SET last_seen_at = CURRENT_TIMESTAMP - INTERVAL '91 seconds'
                WHERE id = 1
                """);
        assertThat(workerPresence.status().online()).isFalse();
        assertThat(runWorkerHeartbeat()).contains("PASS");
        assertThat(workerPresence.status().online()).isTrue();
    }

    private String runWorkerHeartbeat() throws Exception {
        ProcessBuilder builder = new ProcessBuilder("go", "test", "./internal/worker",
                "-run", "^TestInteropWorkerHeartbeat$", "-count=1", "-v");
        builder.directory(findGoModule().toFile()).redirectErrorStream(true);
        builder.environment().put("SKILLHUB_INTEROP_HEARTBEAT", "1");
        builder.environment().put("SKILLHUB_INTEROP_JAVA_URL", baseUrl() + "/internal/github-sync/v1");
        builder.environment().put("SKILLHUB_INTEROP_SERVICE_TOKEN", SERVICE_TOKEN);
        Process process = builder.start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Go worker heartbeat timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as("Go worker heartbeat: %s", output).isZero();
        return output;
    }

    @Test
    void acceptedPackageContinuesAfterCompleteJavaApplicationRestart() throws Exception {
        jdbc.update("""
                UPDATE github_sync_admission_policy
                SET global_high = 1000, global_low = 500, namespace_high = 100, namespace_low = 50
                WHERE id = TRUE
                """);
        long namespaceId = jdbc.queryForObject("""
                INSERT INTO namespace (slug, display_name, type)
                VALUES ('interop-restart-team', 'Interop Restart Team', 'TEAM') RETURNING id
                """, Long.class);
        long repositoryId = 10023;
        long sourceId = jdbc.queryForObject("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility,
                     next_check_at)
                VALUES (?, ?, 'openai', 'skills', 'PUBLIC',
                        CURRENT_TIMESTAMP + INTERVAL '1 day') RETURNING id
                """, Long.class, namespaceId, repositoryId);
        UUID taskId = createRepositoryTask(namespaceId, sourceId, repositoryId);
        UUID ingestionId;
        try (JavaApplicationProcess first = startJavaApplication(3_600_000)) {
            // The first fixed-delay tick runs at startup; receive the package after that tick.
            Thread.sleep(1_000);
            ingestionId = UUID.fromString(runGoClient(taskId, first.port()));
            assertThat(ingestionStatus(ingestionId)).isEqualTo("RECEIVED");
        }
        assertThat(ingestionStatus(ingestionId)).isEqualTo("RECEIVED");

        try (JavaApplicationProcess restarted = startJavaApplication(500)) {
            Instant deadline = Instant.now().plusSeconds(90);
            while (!"READY_FOR_REVIEW".equals(ingestionStatus(ingestionId))
                    && Instant.now().isBefore(deadline)) {
                assertThat(restarted.process().isAlive()).isTrue();
                Thread.sleep(250);
            }
            assertThat(ingestionStatus(ingestionId)).isEqualTo("READY_FOR_REVIEW");
        }
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM review_task
                WHERE github_sync_ingestion_id = ? AND origin_type = 'GITHUB_SYNC'
                  AND status = 'PENDING'
                """, Integer.class, ingestionId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM skill_version
                WHERE id = (SELECT published_version_id FROM github_sync_package_ingestion
                            WHERE id = ?)
                """, Integer.class, ingestionId)).isEqualTo(1);
    }

    @Test
    void goLeaseIsReclaimedAfterJavaStopsDuringRepositoryTask() throws Exception {
        jdbc.update("""
                UPDATE github_sync_admission_policy
                SET global_high = 1000, global_low = 500, namespace_high = 100, namespace_low = 50
                WHERE id = TRUE
                """);
        long namespaceId = jdbc.queryForObject("""
                INSERT INTO namespace (slug, display_name, type)
                VALUES ('interop-outage-team', 'Interop Outage Team', 'TEAM') RETURNING id
                """, Long.class);
        long repositoryId = 10026;
        long sourceId = jdbc.queryForObject("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility,
                     next_check_at)
                VALUES (?, ?, 'openai', 'skills', 'PUBLIC',
                        CURRENT_TIMESTAMP + INTERVAL '1 day') RETURNING id
                """, Long.class, namespaceId, repositoryId);
        UUID taskId = createRepositoryTask(namespaceId, sourceId, repositoryId);
        Path archive = Path.of(System.getenv("SKILLHUB_GITHUB_SYNC_ARCHIVE_FIXTURE"));
        Path markerDirectory = Files.createTempDirectory("skillhub-java-outage-");
        Path ready = markerDirectory.resolve("ready");
        Path outage = markerDirectory.resolve("outage");
        Path goOutput = markerDirectory.resolve("go.log");
        Process goProcess = null;
        try {
            int javaPort;
            UUID ingestionId;
            try (JavaApplicationProcess first = startJavaApplication(3_600_000)) {
                javaPort = first.port();
                Thread.sleep(1_000);
                ProcessBuilder builder = goClientBuilder(taskId, null, COMMIT, archive,
                        "wait-for-java-outage", null, "http://127.0.0.1:" + javaPort);
                builder.environment().put("SKILLHUB_INTEROP_READY_MARKER", ready.toString());
                builder.environment().put("SKILLHUB_INTEROP_OUTAGE_MARKER", outage.toString());
                builder.redirectOutput(goOutput.toFile());
                goProcess = builder.start();
                Instant deadline = Instant.now().plusSeconds(60);
                while (!Files.exists(ready) && Instant.now().isBefore(deadline)) {
                    if (!goProcess.isAlive()) {
                        throw new IllegalStateException("Go client stopped before receipt: "
                                + childFailureSummary(goOutput));
                    }
                    Thread.sleep(100);
                }
                assertThat(Files.exists(ready)).isTrue();
                ingestionId = UUID.fromString(Files.readString(ready).trim());
                assertThat(ingestionStatus(ingestionId)).isEqualTo("RECEIVED");
                assertThat(jdbc.queryForObject("SELECT status FROM github_sync_task WHERE id = ?",
                        String.class, taskId)).isEqualTo("LEASED");
            }

            assertThat(goProcess.isAlive()).isTrue();
            Files.createFile(outage);
            assertThat(goProcess.waitFor(15, TimeUnit.SECONDS)).isTrue();
            assertThat(goProcess.exitValue())
                    .as("Go client after Java outage: %s", childFailureSummary(goOutput)).isZero();
            assertThat(Files.readString(goOutput).trim()).isEqualTo(ingestionId.toString());
            assertThat(jdbc.queryForObject("SELECT status FROM github_sync_task WHERE id = ?",
                    String.class, taskId)).isEqualTo("LEASED");

            // Advance the persisted lease clock rather than waiting for the production TTL.
            jdbc.update("""
                    UPDATE github_sync_task
                    SET lease_expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                    WHERE id = ?
                    """, taskId);
            try (JavaApplicationProcess restarted = startJavaApplication(3_600_000, javaPort)) {
                assertThat(runGoClient(taskId, null, COMMIT, archive, "resume-only",
                        ingestionId.toString(), "http://127.0.0.1:" + restarted.port()))
                        .isEqualTo(ingestionId.toString());
            }
            assertThat(jdbc.queryForObject("SELECT status FROM github_sync_task WHERE id = ?",
                    String.class, taskId)).isEqualTo("ACQUISITION_COMPLETE");
            assertThat(jdbc.queryForObject("SELECT lease_generation FROM github_sync_task WHERE id = ?",
                    Long.class, taskId)).isEqualTo(2);
            assertSingleReceivedPackage(taskId, ingestionId.toString());
        } finally {
            if (goProcess != null && goProcess.isAlive()) {
                goProcess.descendants().forEach(ProcessHandle::destroyForcibly);
                goProcess.destroyForcibly();
                goProcess.waitFor(5, TimeUnit.SECONDS);
            }
            Files.deleteIfExists(ready);
            Files.deleteIfExists(outage);
            Files.deleteIfExists(goOutput);
            Files.deleteIfExists(markerDirectory);
        }
    }

    private String ingestionStatus(UUID ingestionId) {
        return jdbc.queryForObject("""
                SELECT status FROM github_sync_package_ingestion WHERE id = ?
                """, String.class, ingestionId);
    }

    private JavaApplicationProcess startJavaApplication(int ingestionIntervalMs) throws Exception {
        return startJavaApplication(ingestionIntervalMs, 0);
    }

    private JavaApplicationProcess startJavaApplication(int ingestionIntervalMs,
            int requestedPort) throws Exception {
        int childPort = requestedPort;
        if (childPort == 0) {
            try (ServerSocket socket = new ServerSocket(0)) {
                childPort = socket.getLocalPort();
            }
        }
        Path log = Files.createTempFile("skillhub-java-restart-", ".log");
        String applicationClasspath = Arrays.stream(
                        System.getProperty("java.class.path").split(File.pathSeparator))
                .filter(entry -> !Path.of(entry).getFileName().toString().equals("test-classes"))
                .collect(Collectors.joining(File.pathSeparator));
        ProcessBuilder builder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dspring.devtools.restart.enabled=false", "-cp", applicationClasspath,
                "com.iflytek.skillhub.SkillhubApplication");
        builder.redirectErrorStream(true).redirectOutput(log.toFile());
        Map<String, String> environment = builder.environment();
        environment.remove("SPRING_PROFILES_ACTIVE");
        environment.put("SERVER_PORT", Integer.toString(childPort));
        environment.put("SPRING_DATASOURCE_URL", POSTGRES.getJdbcUrl());
        environment.put("SPRING_DATASOURCE_USERNAME", POSTGRES.getUsername());
        environment.put("SPRING_DATASOURCE_PASSWORD", POSTGRES.getPassword());
        environment.put("SPRING_DATA_REDIS_HOST", REDIS.getHost());
        environment.put("SPRING_DATA_REDIS_PORT", REDIS.getMappedPort(6379).toString());
        environment.put("SKILLHUB_GITHUB_SYNC_SERVICE_TOKENS", SERVICE_TOKEN);
        environment.put("SKILLHUB_GITHUB_SYNC_CREDENTIAL_KEYS",
                "v1=" + Base64.getEncoder().encodeToString(CREDENTIAL_KEY));
        environment.put("SKILLHUB_GITHUB_SYNC_ACTIVE_CREDENTIAL_KEY_VERSION", "v1");
        environment.put("SKILLHUB_GITHUB_SYNC_INGESTION_VALIDATION_INTERVAL_MS",
                Integer.toString(ingestionIntervalMs));
        environment.put("SKILLHUB_GITHUB_SYNC_PUBLICATION_INTERVAL_MS",
                Integer.toString(ingestionIntervalMs));
        environment.put("SKILLHUB_GITHUB_SYNC_DISCOVERY_SCHEDULER_INTERVAL_MS", "3600000");
        environment.put("SKILLHUB_GITHUB_SYNC_CANDIDATE_SCHEDULER_INTERVAL_MS", "3600000");
        environment.put("SKILLHUB_GITHUB_SYNC_SOURCE_SCHEDULER_INTERVAL_MS", "3600000");
        environment.put("SKILLHUB_GITHUB_SYNC_UPLOAD_CLEANUP_INTERVAL_MS", "3600000");
        environment.put("SKILLHUB_BUILTIN_SKILLS_ENABLED", "false");
        environment.put("SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET",
                "fixture-download-cookie-secret-for-java-restart");
        environment.put("SKILLHUB_SECURITY_SCANNER_URL", scannerBaseUrl);
        environment.put("STORAGE_BASE_PATH", STORAGE_ROOT.toString());
        if (S3_MODE) {
            environment.put("SKILLHUB_STORAGE_PROVIDER", "s3");
            environment.put("SKILLHUB_STORAGE_S3_ENDPOINT", s3Endpoint);
            environment.put("SKILLHUB_STORAGE_S3_BUCKET", S3_BUCKET);
            environment.put("SKILLHUB_STORAGE_S3_ACCESS_KEY", MINIO_ACCESS_KEY);
            environment.put("SKILLHUB_STORAGE_S3_SECRET_KEY", MINIO_SECRET_KEY);
            environment.put("SKILLHUB_STORAGE_S3_AUTO_CREATE_BUCKET", "true");
        } else {
            environment.put("SKILLHUB_STORAGE_PROVIDER", "local");
        }
        Process process = builder.start();
        JavaApplicationProcess application = new JavaApplicationProcess(process, log, childPort);
        try {
            // Full application restarts compete with the parent test JVM and local preview services.
            Instant deadline = Instant.now().plusSeconds(120);
            while (Instant.now().isBefore(deadline)) {
                if (!process.isAlive()) {
                    throw new IllegalStateException("Java application exited before becoming healthy: "
                            + process.exitValue() + ": " + childFailureSummary(log));
                }
                try {
                    HttpURLConnection connection = (HttpURLConnection) URI.create(
                            "http://127.0.0.1:" + childPort + "/actuator/health")
                            .toURL().openConnection();
                    connection.setConnectTimeout(500);
                    connection.setReadTimeout(500);
                    if (connection.getResponseCode() == 200) {
                        connection.disconnect();
                        return application;
                    }
                    connection.disconnect();
                } catch (IOException ignored) {
                    // The application has not bound its HTTP listener yet.
                }
                Thread.sleep(250);
            }
            throw new IllegalStateException("Java application did not become healthy: "
                    + childFailureSummary(log));
        } catch (Exception exception) {
            application.close();
            throw exception;
        }
    }

    private String childFailureSummary(Path log) throws IOException {
        String output = Files.readString(log);
        output = output.replace(POSTGRES.getPassword(), "[redacted]")
                .replace(SERVICE_TOKEN, "[redacted]")
                .replace(MINIO_ACCESS_KEY, "[redacted]")
                .replace(MINIO_SECRET_KEY, "[redacted]")
                .replace(Base64.getEncoder().encodeToString(CREDENTIAL_KEY), "[redacted]");
        return output.substring(Math.max(0, output.length() - 4000));
    }

    private record JavaApplicationProcess(Process process, Path log, int port)
            implements AutoCloseable {
        @Override
        public void close() throws Exception {
            process.destroy();
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
            }
            Files.deleteIfExists(log);
        }
    }

    @Test
    void invalidGoArchiveDoesNotExposeSkillBodyInFailureOutputs() throws Exception {
        String bodyMarker = "SKILL_BODY_" + UUID.randomUUID().toString().replace("-", "");
        Path invalidArchive = archiveWithNestedSkill(bodyMarker);
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
        try {
            jdbc.update("""
                    UPDATE github_sync_admission_policy
                    SET global_high = 1000, global_low = 500,
                        namespace_high = 100, namespace_low = 50
                    WHERE id = TRUE
                    """);
            long namespaceId = managementQueries.platformNamespaceId();
            long repositoryId = 600_000_000L + Integer.toUnsignedLong(UUID.randomUUID().hashCode());
            long sourceId = jdbc.queryForObject("""
                    INSERT INTO github_source_binding
                        (namespace_id, repository_id, owner_login, repository_name, visibility,
                         next_check_at)
                    VALUES (?, ?, 'openai', 'skills', 'PUBLIC',
                            CURRENT_TIMESTAMP + INTERVAL '1 day') RETURNING id
                    """, Long.class, namespaceId, repositoryId);
            UUID taskId = createRepositoryTask(namespaceId, sourceId, repositoryId);
            String ingestionId = runGoClient(taskId, null, COMMIT, invalidArchive);
            ingestionTask.validatePending();

            UUID id = UUID.fromString(ingestionId);
            assertThat(jdbc.queryForObject("""
                    SELECT status FROM github_sync_package_ingestion WHERE id = ?
                    """, String.class, id)).isEqualTo("FAILED");
            assertThat(jdbc.queryForObject("""
                    SELECT last_error_code FROM github_sync_package_ingestion WHERE id = ?
                    """, String.class, id)).isEqualTo("PACKAGE_STRUCTURE_INVALID");
            assertThat(jdbc.queryForObject("""
                    SELECT outcome FROM github_sync_path_result WHERE ingestion_id = ?
                    """, String.class, id)).isEqualTo("TERMINAL_FAILURE");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM review_task WHERE github_sync_ingestion_id = ?
                    """, Integer.class, id)).isZero();

            String persisted = jdbc.queryForObject("""
                    SELECT row_to_json(failure)::text FROM (
                        SELECT task.snapshot_json, task.last_error_code AS task_error,
                               ingestion.last_error_code AS ingestion_error, path.reason
                        FROM github_sync_task task
                        JOIN github_sync_package_ingestion ingestion ON ingestion.task_id = task.id
                        JOIN github_sync_path_result path ON path.ingestion_id = ingestion.id
                        WHERE ingestion.id = ?
                    ) failure
                    """, String.class, id);
            String operatorView = objectMapper.writeValueAsString(managementQueries.runs(namespaceId, 0, 10))
                    + objectMapper.writeValueAsString(managementQueries.sources(namespaceId, 0, 10))
                    + objectMapper.writeValueAsString(managementQueries.skillPaths(namespaceId, sourceId, 0, 10));
            var admin = authentication(new UsernamePasswordAuthenticationToken(
                    new PlatformPrincipal("github-sync-publisher", "privacy-admin", null,
                            null, null, Set.of("SUPER_ADMIN")), null,
                    List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
            String apiView = mockMvc.perform(get("/api/v1/admin/github-sync/runs").with(admin))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()
                    + mockMvc.perform(get("/api/v1/admin/github-sync/sources").with(admin))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()
                    + mockMvc.perform(get("/api/v1/admin/github-sync/sources/{sourceId}/skills", sourceId)
                            .with(admin))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String logText = logs.list.stream().map(event -> event.getFormattedMessage()
                    + (event.getThrowableProxy() == null ? ""
                    : ThrowableProxyUtil.asString(event.getThrowableProxy()))
                    + event.getMDCPropertyMap()).collect(Collectors.joining("\n"));
            String metricIds = meterRegistry.getMeters().stream().map(meter -> meter.getId().toString())
                    .collect(Collectors.joining("\n"));
            assertThat(persisted).doesNotContain(bodyMarker);
            assertThat(operatorView).doesNotContain(bodyMarker);
            assertThat(apiView).contains("PACKAGE_STRUCTURE_INVALID").doesNotContain(bodyMarker);
            assertThat(logText).doesNotContain(bodyMarker);
            assertThat(metricIds).doesNotContain(bodyMarker);
        } finally {
            root.detachAppender(logs);
            logs.stop();
            Files.deleteIfExists(invalidArchive);
        }
    }

    @Test
    void scannerHttpFailureDoesNotExposeSkillBodyInOperatorOutputs() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(scanner != null, "requires the isolated scanner fixture");
        String bodyMarker = "SKILL_BODY_" + UUID.randomUUID().toString().replace("-", "");
        Path archive = revisionArchive(bodyMarker);
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
        try {
            jdbc.update("""
                    UPDATE github_sync_admission_policy
                    SET global_high = 1000, global_low = 500,
                        namespace_high = 100, namespace_low = 50
                    WHERE id = TRUE
                    """);
            String namespaceSlug = "interop-scan-" + UUID.randomUUID().toString().substring(0, 8);
            long namespaceId = jdbc.queryForObject("""
                    INSERT INTO namespace (slug, display_name, type)
                    VALUES (?, 'Interop Scanner Failure', 'TEAM') RETURNING id
                    """, Long.class, namespaceSlug);
            long repositoryId = 700_000_000L + Integer.toUnsignedLong(UUID.randomUUID().hashCode());
            long sourceId = jdbc.queryForObject("""
                    INSERT INTO github_source_binding
                        (namespace_id, repository_id, owner_login, repository_name, visibility,
                         next_check_at)
                    VALUES (?, ?, 'openai', 'skills', 'NAMESPACE_ONLY',
                            CURRENT_TIMESTAMP + INTERVAL '1 day') RETURNING id
                    """, Long.class, namespaceId, repositoryId);
            UUID taskId = createRepositoryTask(namespaceId, sourceId, repositoryId);
            UUID ingestionId = UUID.fromString(runGoClient(taskId, null, COMMIT, archive));
            ingestionTask.validatePending();
            publicationTask.publishValidated();
            Long versionId = jdbc.queryForObject("""
                    SELECT published_version_id FROM github_sync_package_ingestion WHERE id = ?
                    """, Long.class, ingestionId);
            assertThat(versionId).isNotNull();
            assertThat(jdbc.queryForObject("SELECT status FROM skill_version WHERE id = ?",
                    String.class, versionId)).isEqualTo("SCANNING");

            scannerFailureMarker.set(bodyMarker);
            scanTaskOutboxDispatcher.dispatch();
            awaitVersionStatus(versionId, "SCAN_FAILED");
            assertThat(jdbc.queryForObject("""
                    SELECT failure_reason FROM security_audit WHERE skill_version_id = ?
                    """, String.class, versionId)).doesNotContain(bodyMarker);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM skill WHERE latest_version_id = ?
                    """, Integer.class, versionId)).isZero();

            String persisted = jdbc.queryForObject("""
                    SELECT row_to_json(failure)::text FROM (
                        SELECT task.snapshot_json, task.last_error_code AS task_error,
                               ingestion.last_error_code AS ingestion_error, path.reason,
                               audit.failure_reason
                        FROM github_sync_task task
                        JOIN github_sync_package_ingestion ingestion ON ingestion.task_id = task.id
                        JOIN github_sync_path_result path ON path.ingestion_id = ingestion.id
                        JOIN security_audit audit ON audit.skill_version_id = ingestion.published_version_id
                        WHERE ingestion.id = ?
                    ) failure
                    """, String.class, ingestionId);
            String reviewerId = "privacy-scanner-" + UUID.randomUUID();
            jdbc.update("INSERT INTO user_account (id, display_name) VALUES (?, 'Privacy Scanner Reviewer')",
                    reviewerId);
            jdbc.update("""
                    INSERT INTO namespace_member (namespace_id, user_id, role, created_at, updated_at)
                    VALUES (?, ?, 'OWNER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, namespaceId, reviewerId);
            var admin = authentication(new UsernamePasswordAuthenticationToken(
                    new PlatformPrincipal(reviewerId, "privacy-admin", null,
                            null, null, Set.of("SUPER_ADMIN")), null,
                    List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
            String apiView = mockMvc.perform(get("/api/v1/namespaces/{namespaceSlug}/github-sources/runs",
                            namespaceSlug).with(admin))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()
                    + mockMvc.perform(get("/api/v1/namespaces/{namespaceSlug}/github-sources/resolved",
                            namespaceSlug).with(admin))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()
                    + mockMvc.perform(get("/api/v1/namespaces/{namespaceSlug}/github-sources/resolved/{sourceId}/skills",
                            namespaceSlug, sourceId)
                            .with(admin))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String logText = logs.list.stream().map(event -> event.getFormattedMessage()
                    + (event.getThrowableProxy() == null ? ""
                    : ThrowableProxyUtil.asString(event.getThrowableProxy()))
                    + event.getMDCPropertyMap()).collect(Collectors.joining("\n"));
            String metricIds = meterRegistry.getMeters().stream().map(meter -> meter.getId().toString())
                    .collect(Collectors.joining("\n"));
            assertThat(persisted).doesNotContain(bodyMarker);
            assertThat(apiView).doesNotContain(bodyMarker);
            assertThat(logText).doesNotContain(bodyMarker);
            assertThat(metricIds).doesNotContain(bodyMarker);
        } finally {
            scannerFailureMarker.set(null);
            root.detachAppender(logs);
            logs.stop();
            Files.deleteIfExists(archive);
        }
    }

    @Test
    void goArchiveCanTravelThroughHttpReceiptAndPersistedIngestion() throws Exception {
        byte[] archive = Files.readAllBytes(Path.of(System.getenv("SKILLHUB_GITHUB_SYNC_ARCHIVE_FIXTURE")));
        assertThat(archive).isNotEmpty();
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archive));
        long namespaceId = jdbc.queryForObject("""
                SELECT id FROM namespace WHERE slug = 'github' AND type = 'CATALOG'
                """, Long.class);
        long sourceId = jdbc.queryForObject("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility,
                     next_check_at)
                VALUES (?, 10001, 'openai', 'skills', 'PUBLIC',
                        CURRENT_TIMESTAMP + INTERVAL '1 day') RETURNING id
                """, Long.class, namespaceId);
        jdbc.update("""
                UPDATE github_sync_admission_policy
                SET global_high = 1000, global_low = 500, namespace_high = 100, namespace_low = 50
                WHERE id = TRUE
                """);
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID oversizedTaskId = S3_MODE ? UUID.randomUUID() : null;
        jdbc.update("""
                INSERT INTO github_sync_run (id, namespace_id, kind)
                VALUES (?, ?, 'REPOSITORY_SYNC')
                """, runId, namespaceId);
        jdbc.update("""
                INSERT INTO github_sync_task
                    (id, run_id, namespace_id, source_id, kind, mode, dedupe_key, priority,
                     snapshot_json, fixed_commit_sha)
                VALUES (?, ?, ?, ?, 'REPOSITORY_SYNC', 'FULL', ?, 100,
                    '{"repositoryId":10001,"owner":"openai","name":"skills"}'::jsonb, ?)
                """, taskId, runId, namespaceId, sourceId, "repository:10001", COMMIT);

        if (S3_MODE) {
            UUID oversizedRunId = UUID.randomUUID();
            long oversizedSourceId = jdbc.queryForObject("""
                    INSERT INTO github_source_binding
                        (namespace_id, repository_id, owner_login, repository_name, visibility,
                         next_check_at)
                    VALUES (?, 10002, 'openai', 'oversized-fixture', 'PUBLIC',
                            CURRENT_TIMESTAMP + INTERVAL '1 day') RETURNING id
                    """, Long.class, namespaceId);
            jdbc.update("""
                    INSERT INTO github_sync_run (id, namespace_id, kind)
                    VALUES (?, ?, 'REPOSITORY_SYNC')
                    """, oversizedRunId, namespaceId);
            jdbc.update("""
                    INSERT INTO github_sync_task
                        (id, run_id, namespace_id, source_id, kind, mode, dedupe_key, priority,
                         snapshot_json, fixed_commit_sha)
                    VALUES (?, ?, ?, ?, 'REPOSITORY_SYNC', 'FULL', ?, 90,
                        '{"repositoryId":10002,"owner":"openai","name":"skills"}'::jsonb, ?)
                    """, oversizedTaskId, oversizedRunId, namespaceId, oversizedSourceId,
                    "repository:10002", COMMIT);
        }

        String ingestionId = runGoClient(taskId, oversizedTaskId);
        if (S3_MODE) {
            UUID uploadId = jdbc.queryForObject("""
                    SELECT upload_id FROM github_sync_package_ingestion WHERE id = ?
                    """, UUID.class, UUID.fromString(ingestionId));
            assertThat(storage.exists("github-sync/staging/" + uploadId + ".zip.forbidden"))
                    .isFalse();
            String objectKey = jdbc.queryForObject("""
                    SELECT object_key FROM github_sync_staged_upload WHERE id = ?
                    """, String.class, uploadId);
            assertThat(storage.getMetadata(objectKey).size()).isEqualTo(archive.length);
            StoragePrefixUsage stagedUsage = storage.listPrefixUsage("github-sync/staging/", 10_000)
                    .orElseThrow();
            assertThat(stagedUsage.complete()).isTrue();
            assertThat(stagedUsage.objectCount()).isPositive();
            assertThat(stagedUsage.bytes()).isGreaterThanOrEqualTo(archive.length);
            capacityMetrics.sampleS3();
            assertThat(meterRegistry.get("skillhub.github.sync.staging.s3.used.bytes")
                    .gauge().value()).isEqualTo(stagedUsage.bytes());
            assertThat(meterRegistry.get("skillhub.github.sync.staging.s3.capacity.warning")
                    .gauge().value()).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_package_ingestion
                WHERE id = ? AND task_id = ? AND sha256 = ? AND size_bytes = ?
                """, Integer.class, UUID.fromString(ingestionId), taskId, digest, archive.length)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM github_sync_task WHERE id = ?",
                String.class, taskId)).isEqualTo("ACQUISITION_COMPLETE");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_path_result
                WHERE task_id = ? AND commit_sha = ? AND skill_path = ?
                  AND outcome = 'ACCEPTED' AND ingestion_id = ?
                """, Integer.class, taskId, COMMIT, SKILL_PATH, UUID.fromString(ingestionId))).isEqualTo(1);
        claimInSeparateJvm(ingestionId, IngestionClaimProcess.class);
        Timestamp validationExpiry = jdbc.queryForObject("""
                SELECT validation_expires_at FROM github_sync_package_ingestion WHERE id = ?
                """, Timestamp.class, UUID.fromString(ingestionId));
        UUID expiredToken = jdbc.queryForObject("""
                SELECT validation_token FROM github_sync_package_ingestion WHERE id = ?
                """, UUID.class, UUID.fromString(ingestionId));
        assertThat(jdbc.queryForObject("""
                SELECT status FROM github_sync_package_ingestion WHERE id = ?
                """, String.class, UUID.fromString(ingestionId))).isEqualTo("VALIDATING");
        Thread.sleep(Math.max(0, Duration.between(Instant.now(),
                validationExpiry.toInstant()).toMillis() + 100));
        ingestionTask.validatePending();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_package_ingestion
                WHERE id = ? AND validated_at IS NOT NULL AND content_sha256 IS NOT NULL
                  AND status IN ('VALIDATED', 'READY_FOR_REVIEW') AND validation_attempts = 2
                """, Integer.class, UUID.fromString(ingestionId))).isEqualTo(1);
        assertThat(ingestions.validated(UUID.fromString(ingestionId), expiredToken,
                "0".repeat(64), 0, "{}", Instant.now())).isFalse();
        claimInSeparateJvm(ingestionId, PublicationClaimProcess.class);
        Timestamp publicationExpiry = jdbc.queryForObject("""
                SELECT publication_expires_at FROM github_sync_package_ingestion WHERE id = ?
                """, Timestamp.class, UUID.fromString(ingestionId));
        UUID expiredPublicationToken = jdbc.queryForObject("""
                SELECT publication_token FROM github_sync_package_ingestion WHERE id = ?
                """, UUID.class, UUID.fromString(ingestionId));
        assertThat(jdbc.queryForObject("""
                SELECT status FROM github_sync_package_ingestion WHERE id = ?
                """, String.class, UUID.fromString(ingestionId))).isEqualTo("VALIDATED");
        Thread.sleep(Math.max(0, Duration.between(Instant.now(),
                publicationExpiry.toInstant()).toMillis() + 100));
        var expiredPublicationClaim = new GithubSyncPublicationRepository.ClaimedPublication(
                UUID.fromString(ingestionId), expiredPublicationToken, namespaceId, "github",
                10001, sourceId, SKILL_PATH, "ACTIVE", "PUBLIC", "", archive.length,
                digest, "0".repeat(64), null, 1);
        assertThat(publications.noChange(expiredPublicationClaim, Instant.now())).isFalse();
        publicationTask.publishValidated();
        Long versionId = jdbc.queryForObject("""
                SELECT published_version_id FROM github_sync_package_ingestion WHERE id = ?
                """, Long.class, UUID.fromString(ingestionId));
        assertThat(versionId).isNotNull();
        assertThat(jdbc.queryForObject("""
                SELECT status FROM github_sync_package_ingestion WHERE id = ?
                """, String.class, UUID.fromString(ingestionId))).isEqualTo("READY_FOR_REVIEW");
        assertThat(jdbc.queryForObject("""
                SELECT publication_attempts FROM github_sync_package_ingestion WHERE id = ?
                """, Integer.class, UUID.fromString(ingestionId))).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM review_task
                WHERE skill_version_id = ? AND origin_type = 'GITHUB_SYNC'
                  AND github_sync_ingestion_id = ? AND status = 'PENDING'
                """, Integer.class, versionId, UUID.fromString(ingestionId))).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM review_task
                WHERE skill_version_id = ? AND origin_type = 'USER'
                """, Integer.class, versionId)).isZero();
        assertThat(reviews.findByStatus(ReviewTaskStatus.PENDING, PageRequest.of(0, 10))
                .getTotalElements()).isZero();
        assertThat(reviews.findByOriginTypeAndStatus(ReviewOriginType.GITHUB_SYNC,
                ReviewTaskStatus.PENDING, PageRequest.of(0, 10)).getTotalElements()).isPositive();
        assertThat(jdbc.queryForObject("""
                SELECT version.status FROM skill_version version WHERE version.id = ?
                """, String.class, versionId)).isEqualTo("SCANNING");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM scan_task_outbox
                WHERE version_id = ? AND status = 'PENDING'
                """, Integer.class, versionId)).isEqualTo(1);
        scanTaskOutboxDispatcher.dispatch();
        awaitVersionStatus(versionId, "PENDING_REVIEW");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM security_audit
                WHERE skill_version_id = ? AND is_safe = TRUE AND scanned_at IS NOT NULL
                """, Integer.class, versionId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM skill WHERE latest_version_id = ?
                """, Integer.class, versionId)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM skill
                WHERE id = (SELECT skill_id FROM skill_version WHERE id = ?)
                  AND namespace_id = ? AND owner_id = 'github-sync-publisher'
                  AND github_sync_managed = TRUE AND visibility = 'PUBLIC'
                """, Integer.class, versionId, namespaceId)).isEqualTo(1);
        String platformSlug = skillSlug(versionId);
        assertThat(anonymousSkillDetail("github", platformSlug).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_source_skill_binding
                WHERE source_id = ? AND skill_path = ? AND latest_ingestion_id = ?
                """, Integer.class, sourceId, SKILL_PATH,
                UUID.fromString(ingestionId))).isEqualTo(1);
        publicationTask.publishValidated();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM review_task WHERE github_sync_ingestion_id = ?
                """, Integer.class, UUID.fromString(ingestionId))).isEqualTo(1);
        if (S3_MODE) {
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM github_sync_package_ingestion WHERE task_id = ?
                    """, Integer.class, oversizedTaskId)).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT upload.status FROM github_sync_staged_upload upload
                    JOIN github_sync_package_admission admission ON admission.id = upload.admission_id
                    WHERE admission.task_id = ?
                    """, String.class, oversizedTaskId)).isEqualTo("FAILED");
            String oversizedObjectKey = jdbc.queryForObject("""
                    SELECT upload.object_key FROM github_sync_staged_upload upload
                    JOIN github_sync_package_admission admission ON admission.id = upload.admission_id
                    WHERE admission.task_id = ?
                    """, String.class, oversizedTaskId);
            assertThat(storage.exists(oversizedObjectKey)).isFalse();
        }

        UUID repeatRunId = UUID.randomUUID();
        UUID repeatTaskId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO github_sync_run (id, namespace_id, kind)
                VALUES (?, ?, 'REPOSITORY_SYNC')
                """, repeatRunId, namespaceId);
        jdbc.update("""
                INSERT INTO github_sync_task
                    (id, run_id, namespace_id, source_id, kind, mode, dedupe_key, priority,
                     snapshot_json, fixed_commit_sha)
                VALUES (?, ?, ?, ?, 'REPOSITORY_SYNC', 'FULL', ?, 100,
                    '{"repositoryId":10001,"owner":"openai","name":"skills"}'::jsonb, ?)
                """, repeatTaskId, repeatRunId, namespaceId, sourceId,
                "interop-repeat:" + repeatTaskId, COMMIT);
        String repeatIngestionId = runGoClient(repeatTaskId, null);
        ingestionTask.validatePending();
        publicationTask.publishValidated();
        assertThat(jdbc.queryForObject("""
                SELECT status FROM github_sync_package_ingestion WHERE id = ?
                """, String.class, UUID.fromString(repeatIngestionId))).isEqualTo("NO_CHANGE");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM skill_version WHERE skill_id =
                    (SELECT skill_id FROM skill_version WHERE id = ?)
                """, Integer.class, versionId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM review_task
                WHERE origin_type = 'GITHUB_SYNC' AND namespace_id = ?
                """, Integer.class, namespaceId)).isEqualTo(1);

        jdbc.update("""
                INSERT INTO user_account (id, display_name)
                VALUES ('interop-reviewer', 'Interop Reviewer')
                ON CONFLICT (id) DO NOTHING
                """);
        Long reviewId = jdbc.queryForObject("""
                SELECT id FROM review_task WHERE github_sync_ingestion_id = ?
                """, Long.class, UUID.fromString(ingestionId));
        reviewService.approveReview(reviewId, "interop-reviewer", "License evidence checked",
                Map.of(), Set.of("SUPER_ADMIN"));
        assertThat(jdbc.queryForObject("""
                SELECT status FROM github_sync_package_ingestion WHERE id = ?
                """, String.class, UUID.fromString(ingestionId))).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("""
                SELECT status FROM skill_version WHERE id = ?
                """, String.class, versionId)).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM skill WHERE latest_version_id = ?
                """, Integer.class, versionId)).isEqualTo(1);
        HttpResponse<String> publicDetail = anonymousSkillDetail("github", platformSlug);
        assertThat(publicDetail.statusCode()).isEqualTo(200);
        var publicData = objectMapper.readTree(publicDetail.body()).path("data");
        assertThat(publicData.path("publishedVersion").path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(publicData.path("githubSource").path("ownerLogin").asText()).isEqualTo("openai");
        assertThat(publicData.path("githubSource").path("commitSha").asText()).isEqualTo(COMMIT);

        long teamNamespaceId = jdbc.queryForObject("""
                INSERT INTO namespace (slug, display_name, type)
                VALUES ('interop-team', 'Interop Team', 'TEAM') RETURNING id
                """, Long.class);
        long teamSourceId = jdbc.queryForObject("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility)
                VALUES (?, 10003, 'openai', 'skills', 'NAMESPACE_ONLY') RETURNING id
                """, Long.class, teamNamespaceId);
        UUID teamTaskId = createRepositoryTask(teamNamespaceId, teamSourceId, 10003);
        String teamIngestionId = runGoClient(teamTaskId, null);
        ingestionTask.validatePending();
        publicationTask.publishValidated();
        Long teamVersionId = jdbc.queryForObject("""
                SELECT published_version_id FROM github_sync_package_ingestion WHERE id = ?
                """, Long.class, UUID.fromString(teamIngestionId));
        assertThat(teamVersionId).isNotNull();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM skill
                WHERE id = (SELECT skill_id FROM skill_version WHERE id = ?)
                  AND namespace_id = ? AND owner_id = 'github-sync-publisher'
                  AND visibility = 'NAMESPACE_ONLY' AND latest_version_id IS NULL
                """, Integer.class, teamVersionId, teamNamespaceId)).isEqualTo(1);
        scanTaskOutboxDispatcher.dispatch();
        awaitVersionStatus(teamVersionId, "PENDING_REVIEW");
        Long teamReviewId = jdbc.queryForObject("""
                SELECT id FROM review_task WHERE github_sync_ingestion_id = ?
                """, Long.class, UUID.fromString(teamIngestionId));
        reviewService.rejectReview(teamReviewId, "interop-reviewer", "Not ready for team use",
                Map.of(), Set.of("SUPER_ADMIN"));
        assertThat(jdbc.queryForObject("""
                SELECT status FROM github_sync_package_ingestion WHERE id = ?
                """, String.class, UUID.fromString(teamIngestionId))).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM skill WHERE latest_version_id = ?
                """, Integer.class, teamVersionId)).isZero();

        UUID teamRepeatTaskId = createRepositoryTask(teamNamespaceId, teamSourceId, 10003);
        String teamRepeatIngestionId = runGoClient(teamRepeatTaskId, null);
        ingestionTask.validatePending();
        publicationTask.publishValidated();
        assertThat(jdbc.queryForObject("""
                SELECT status FROM github_sync_package_ingestion WHERE id = ?
                """, String.class, UUID.fromString(teamRepeatIngestionId))).isEqualTo("NO_CHANGE");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM review_task WHERE namespace_id = ? AND origin_type = 'GITHUB_SYNC'
                """, Integer.class, teamNamespaceId)).isEqualTo(1);

        assertThat(publications.requestRejectedRetry(teamNamespaceId, teamSourceId + 1,
                SKILL_PATH, Instant.now())).isFalse();
        assertThat(publications.requestRejectedRetry(teamNamespaceId, teamSourceId,
                SKILL_PATH, Instant.now())).isTrue();
        assertThat(sourceTasks.enqueueDue(Instant.now(), 100)).isPositive();
        UUID retryTaskId = jdbc.queryForObject("""
                SELECT id FROM github_sync_task
                WHERE source_id = ? AND status = 'QUEUED'
                ORDER BY created_at DESC LIMIT 1
                """, UUID.class, teamSourceId);
        assertThat(jdbc.queryForObject("SELECT mode FROM github_sync_task WHERE id = ?",
                String.class, retryTaskId)).isEqualTo("FULL");
        assertThat(jdbc.queryForObject("""
                SELECT retry_scheduled_at FROM github_source_skill_binding
                WHERE source_id = ? AND skill_path = ?
                """, Timestamp.class, teamSourceId, SKILL_PATH)).isNotNull();
        jdbc.update("UPDATE github_sync_task SET status = 'FAILED' WHERE id = ?", retryTaskId);
        sourceTasks.enqueueDue(Instant.now(), 100);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_task
                WHERE source_id = ? AND status = 'QUEUED'
                """, Integer.class, teamSourceId)).isZero();
        assertThat(publications.requestRejectedRetry(teamNamespaceId, teamSourceId,
                SKILL_PATH, Instant.now())).isTrue();
        assertThat(sourceTasks.enqueueDue(Instant.now(), 100)).isPositive();
        retryTaskId = jdbc.queryForObject("""
                SELECT id FROM github_sync_task
                WHERE source_id = ? AND status = 'QUEUED'
                ORDER BY created_at DESC LIMIT 1
                """, UUID.class, teamSourceId);
        jdbc.update("UPDATE github_sync_task SET fixed_commit_sha = ? WHERE id = ?", COMMIT, retryTaskId);
        String retriedIngestionId = runGoClient(retryTaskId, null);
        ingestionTask.validatePending();
        publicationTask.publishValidated();
        assertThat(jdbc.queryForObject("""
                SELECT status FROM github_sync_package_ingestion WHERE id = ?
                """, String.class, UUID.fromString(retriedIngestionId))).isEqualTo("READY_FOR_REVIEW");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM review_task WHERE namespace_id = ? AND origin_type = 'GITHUB_SYNC'
                """, Integer.class, teamNamespaceId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT retry_requested_at FROM github_source_skill_binding
                WHERE source_id = ? AND skill_path = ?
                """, Timestamp.class, teamSourceId, SKILL_PATH)).isNull();
        assertThat(jdbc.queryForObject("""
                SELECT retry_scheduled_at FROM github_source_skill_binding
                WHERE source_id = ? AND skill_path = ?
                """, Timestamp.class, teamSourceId, SKILL_PATH)).isNull();

        Path revisionA = revisionArchive("a");
        Path revisionB = revisionArchive("b");
        Path revisionC = revisionArchive("c");
        Path revisionD = revisionArchive("d");
        Path licenseRevision = revisionArchive("d", "v2");
        try {
            UUID firstUpdateTask = createRepositoryTask(teamNamespaceId, teamSourceId, 10003,
                    "a".repeat(40));
            String firstUpdateId = runGoClient(firstUpdateTask, null, "a".repeat(40), revisionA);
            ingestionTask.validatePending();
            publicationTask.publishValidated();
            Long firstUpdateReview = jdbc.queryForObject("""
                    SELECT id FROM review_task WHERE github_sync_ingestion_id = ?
                    """, Long.class, UUID.fromString(firstUpdateId));
            assertThat(firstUpdateReview).isNotNull();

            UUID secondUpdateTask = createRepositoryTask(teamNamespaceId, teamSourceId, 10003,
                    "b".repeat(40));
            String secondUpdateId = runGoClient(secondUpdateTask, null, "b".repeat(40), revisionB);
            ingestionTask.validatePending();
            publicationTask.publishValidated();
            Long secondUpdateReview = jdbc.queryForObject("""
                    SELECT id FROM review_task WHERE github_sync_ingestion_id = ?
                    """, Long.class, UUID.fromString(secondUpdateId));
            Long secondUpdateVersion = jdbc.queryForObject("""
                    SELECT published_version_id FROM github_sync_package_ingestion WHERE id = ?
                    """, Long.class, UUID.fromString(secondUpdateId));
            assertThat(jdbc.queryForObject("SELECT status FROM review_task WHERE id = ?",
                    String.class, firstUpdateReview)).isEqualTo("SUPERSEDED");
            assertThat(jdbc.queryForObject("SELECT status FROM github_sync_package_ingestion WHERE id = ?",
                    String.class, UUID.fromString(firstUpdateId))).isEqualTo("SUPERSEDED");
            assertThat(jdbc.queryForObject("SELECT status FROM review_task WHERE id = ?",
                    String.class, secondUpdateReview)).isEqualTo("PENDING");
            assertThatThrownBy(() -> reviewService.approveReview(firstUpdateReview,
                    "interop-reviewer", "obsolete", Map.of(), Set.of("SUPER_ADMIN")))
                    .hasMessageContaining("review.not_pending");
            scanTaskOutboxDispatcher.dispatch();
            awaitVersionStatus(secondUpdateVersion, "PENDING_REVIEW");
            reviewService.approveReview(secondUpdateReview, "interop-reviewer", "Current revision",
                    Map.of(), Set.of("SUPER_ADMIN"));
            assertThat(jdbc.queryForObject("""
                    SELECT latest_version_id FROM skill
                    WHERE id = (SELECT skill_id FROM skill_version WHERE id = ?)
                    """, Long.class, secondUpdateVersion)).isEqualTo(secondUpdateVersion);
            assertThat(anonymousSkillDetail("interop-team", skillSlug(secondUpdateVersion))
                    .statusCode()).isEqualTo(403);

            UUID olderTask = createRepositoryTask(teamNamespaceId, teamSourceId, 10003,
                    "c".repeat(40));
            String olderId = runGoClient(olderTask, null, "c".repeat(40), revisionC);
            UUID newerTask = createRepositoryTask(teamNamespaceId, teamSourceId, 10003,
                    "d".repeat(40));
            String newerId = runGoClient(newerTask, null, "d".repeat(40), revisionD);
            ingestionTask.validatePending();
            jdbc.update("""
                    UPDATE github_sync_package_ingestion
                    SET publication_retry_at = CURRENT_TIMESTAMP + INTERVAL '1 hour'
                    WHERE id = ?
                    """, UUID.fromString(olderId));
            publicationTask.publishValidated();
            assertThat(jdbc.queryForObject("""
                    SELECT latest_ingestion_id FROM github_source_skill_binding
                    WHERE source_id = ? AND skill_path = ?
                    """, UUID.class, teamSourceId, SKILL_PATH)).isEqualTo(UUID.fromString(newerId));
            jdbc.update("""
                    UPDATE github_sync_package_ingestion SET publication_retry_at = NULL WHERE id = ?
                    """, UUID.fromString(olderId));
            publicationTask.publishValidated();
            assertThat(jdbc.queryForObject("""
                    SELECT status FROM github_sync_package_ingestion WHERE id = ?
                    """, String.class, UUID.fromString(olderId))).isEqualTo("SUPERSEDED");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM review_task WHERE github_sync_ingestion_id = ?
                    """, Integer.class, UUID.fromString(olderId))).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT latest_ingestion_id FROM github_source_skill_binding
                    WHERE source_id = ? AND skill_path = ?
                    """, UUID.class, teamSourceId, SKILL_PATH)).isEqualTo(UUID.fromString(newerId));
            assertThat(jdbc.queryForObject("""
                    SELECT status FROM review_task WHERE github_sync_ingestion_id = ?
                    """, String.class, UUID.fromString(newerId))).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("""
                    SELECT latest_version_id FROM skill
                    WHERE id = (SELECT skill_id FROM skill_version WHERE id = ?)
                    """, Long.class, secondUpdateVersion)).isEqualTo(secondUpdateVersion);

            UUID sameContentTask = createRepositoryTask(teamNamespaceId, teamSourceId, 10003,
                    "e".repeat(40));
            String sameContentId = runGoClient(sameContentTask, null,
                    "e".repeat(40), revisionD);
            ingestionTask.validatePending();
            publicationTask.publishValidated();
            assertThat(jdbc.queryForObject("""
                    SELECT status FROM github_sync_package_ingestion WHERE id = ?
                    """, String.class, UUID.fromString(sameContentId))).isEqualTo("NO_CHANGE");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM review_task WHERE github_sync_ingestion_id = ?
                    """, Integer.class, UUID.fromString(sameContentId))).isZero();

            UUID licenseTask = createRepositoryTask(teamNamespaceId, teamSourceId, 10003,
                    "f".repeat(40));
            String licenseId = runGoClient(licenseTask, null,
                    "f".repeat(40), licenseRevision);
            ingestionTask.validatePending();
            publicationTask.publishValidated();
            assertThat(jdbc.queryForObject("""
                    SELECT status FROM github_sync_package_ingestion WHERE id = ?
                    """, String.class, UUID.fromString(licenseId))).isEqualTo("READY_FOR_REVIEW");
            assertThat(jdbc.queryForObject("""
                    SELECT content_sha256 FROM github_sync_package_ingestion WHERE id = ?
                    """, String.class, UUID.fromString(licenseId))).isNotEqualTo(
                    jdbc.queryForObject("SELECT content_sha256 FROM github_sync_package_ingestion WHERE id = ?",
                            String.class, UUID.fromString(newerId)));
            assertThat(jdbc.queryForObject("""
                    SELECT status FROM review_task WHERE github_sync_ingestion_id = ?
                    """, String.class, UUID.fromString(newerId))).isEqualTo("SUPERSEDED");
            assertThat(jdbc.queryForObject("""
                    SELECT latest_version_id FROM skill
                    WHERE id = (SELECT skill_id FROM skill_version WHERE id = ?)
                    """, Long.class, secondUpdateVersion)).isEqualTo(secondUpdateVersion);
        } finally {
            Files.deleteIfExists(revisionA);
            Files.deleteIfExists(revisionB);
            Files.deleteIfExists(revisionC);
            Files.deleteIfExists(revisionD);
            Files.deleteIfExists(licenseRevision);
        }
    }

    @Test
    void separateGoProcessesResumeAcceptedPackageAfterLeaseExpiry() throws Exception {
        long namespaceId = jdbc.queryForObject("""
                INSERT INTO namespace (slug, display_name, type)
                VALUES ('interop-resume-team', 'Interop Resume Team', 'TEAM') RETURNING id
                """, Long.class);
        jdbc.update("""
                UPDATE github_sync_admission_policy
                SET global_high = 1000, global_low = 500, namespace_high = 100, namespace_low = 50
                WHERE id = TRUE
                """);
        long sourceId = jdbc.queryForObject("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility,
                     next_check_at)
                VALUES (?, 10004, 'openai', 'restart-fixture', 'PUBLIC',
                        CURRENT_TIMESTAMP + INTERVAL '1 day') RETURNING id
                """, Long.class, namespaceId);
        UUID taskId = createRepositoryTask(namespaceId, sourceId, 10004);
        jdbc.update("UPDATE github_sync_task SET priority = 1000 WHERE id = ?", taskId);
        Path archive = Path.of(System.getenv("SKILLHUB_GITHUB_SYNC_ARCHIVE_FIXTURE"));

        String ingestionId;
        try (AutoCloseable ignored = pauseOtherSources(sourceId)) {
            ingestionId = runGoClient(taskId, null, COMMIT, archive, "report-only", null);
            assertThat(jdbc.queryForObject("SELECT status FROM github_sync_task WHERE id = ?",
                    String.class, taskId)).isEqualTo("LEASED");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM github_sync_package_ingestion WHERE task_id = ?
                    """, Integer.class, taskId)).isEqualTo(1);
            jdbc.update("""
                    UPDATE github_sync_task SET lease_expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                    WHERE id = ?
                    """, taskId);

            assertThat(runGoClient(taskId, null, COMMIT, archive, "resume-only", ingestionId))
                    .isEqualTo(ingestionId);
        }
        assertThat(jdbc.queryForObject("""
                SELECT lease_generation FROM github_sync_task WHERE id = ?
                """, Long.class, taskId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM github_sync_task WHERE id = ?",
                String.class, taskId)).isEqualTo("ACQUISITION_COMPLETE");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_package_admission WHERE task_id = ?
                """, Integer.class, taskId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_staged_upload upload
                JOIN github_sync_package_admission admission ON admission.id = upload.admission_id
                WHERE admission.task_id = ?
                """, Integer.class, taskId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_package_ingestion WHERE id = ? AND task_id = ?
                """, Integer.class, UUID.fromString(ingestionId), taskId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_package_ingestion WHERE task_id = ?
                """, Integer.class, taskId)).isEqualTo(1);
    }

    private AutoCloseable pauseOtherSources(long activeSourceId) {
        Map<Long, String> previousStatuses = new LinkedHashMap<>();
        jdbc.query("""
                SELECT id, status FROM github_source_binding
                WHERE id <> ? AND status <> 'PAUSED'
                """, result -> {
                    previousStatuses.put(result.getLong("id"), result.getString("status"));
                }, activeSourceId);
        jdbc.update("""
                UPDATE github_source_binding SET status = 'PAUSED'
                WHERE id <> ? AND status <> 'PAUSED'
                """, activeSourceId);
        return () -> previousStatuses.forEach((id, status) -> jdbc.update("""
                UPDATE github_source_binding SET status = ? WHERE id = ?
                """, status, id));
    }

    @Test
    void repositoryWorkerResumesFailedPathWithoutReacquiringReceivedPackage() throws Exception {
        long namespaceId = jdbc.queryForObject("""
                SELECT id FROM namespace WHERE slug = 'github' AND type = 'CATALOG'
                """, Long.class);
        jdbc.update("""
                UPDATE github_sync_admission_policy
                SET global_high = 1000, global_low = 500, namespace_high = 100, namespace_low = 50
                WHERE id = TRUE
                """);
        long sourceId = jdbc.queryForObject("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility,
                     next_check_at)
                VALUES (?, 42, 'openai', 'skills', 'PUBLIC',
                        CURRENT_TIMESTAMP + INTERVAL '1 day') RETURNING id
                """, Long.class, namespaceId);
        String commit = "c".repeat(40);
        UUID taskId = createRepositoryTask(namespaceId, sourceId, 42, commit);
        Path archive = Path.of(System.getenv("SKILLHUB_GITHUB_SYNC_ARCHIVE_FIXTURE"));

        String ingestionId = runGoClient(taskId, null, commit, archive, "report-only", null);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_package_ingestion WHERE id = ? AND task_id = ?
                """, Integer.class, UUID.fromString(ingestionId), taskId)).isEqualTo(1);
        jdbc.update("""
                INSERT INTO github_sync_path_result
                    (task_id, commit_sha, skill_path, outcome, reason)
                VALUES (?, ?, 'skills/retry', 'RETRYABLE_FAILURE', 'FIXTURE_RETRY')
                """, taskId, commit);
        jdbc.update("""
                UPDATE github_sync_task
                SET path_checkpoint = 'skills/retry',
                    lease_expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE id = ?
                """, taskId);

        runRepositoryWorker(taskId, "retry");
        assertThat(jdbc.queryForObject("SELECT status FROM github_sync_task WHERE id = ?",
                String.class, taskId)).isEqualTo("RETRY_WAIT");
        assertThat(jdbc.queryForObject("""
                SELECT retry_at FROM github_sync_task WHERE id = ?
                """, Timestamp.class, taskId).toInstant()).isAfter(Instant.now());
        assertThat(jdbc.queryForObject("""
                SELECT outcome FROM github_sync_path_result
                WHERE task_id = ? AND commit_sha = ? AND skill_path = 'skills/retry'
                """, String.class, taskId, commit)).isEqualTo("RETRYABLE_FAILURE");
        assertThat(jdbc.queryForObject("""
                SELECT fixed_commit_sha FROM github_sync_task WHERE id = ?
                """, String.class, taskId)).isEqualTo(commit);
        assertSingleReceivedPackage(taskId, ingestionId);

        jdbc.update("""
                UPDATE github_sync_task
                SET retry_at = CURRENT_TIMESTAMP - INTERVAL '1 second',
                    due_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE id = ?
                """, taskId);
        runRepositoryWorker(taskId, "resolve");
        assertThat(jdbc.queryForObject("SELECT status FROM github_sync_task WHERE id = ?",
                String.class, taskId)).isEqualTo("PARTIAL");
        assertThat(jdbc.queryForObject("""
                SELECT outcome FROM github_sync_path_result
                WHERE task_id = ? AND commit_sha = ? AND skill_path = 'skills/retry'
                """, String.class, taskId, commit)).isEqualTo("TERMINAL_FAILURE");
        assertThat(jdbc.queryForObject("""
                SELECT lease_generation FROM github_sync_task WHERE id = ?
                """, Long.class, taskId)).isEqualTo(3);
        assertSingleReceivedPackage(taskId, ingestionId);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SKILLHUB_GITHUB_SYNC_LIVE_WORKER", matches = "1")
    void repositoryWorkerAcquiresTwoPublicSkillsThroughJava() throws Exception {
        boolean realTree = "1".equals(System.getenv("SKILLHUB_GITHUB_SYNC_LIVE_WORKER_REAL_TREE"));
        long repositoryId = realTree ? 1103643573L : 987654350L;
        byte[] referenceArchive = Files.readAllBytes(Path.of(
                System.getenv("SKILLHUB_GITHUB_SYNC_ARCHIVE_FIXTURE")));
        String referenceDigest = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(referenceArchive));
        String namespaceSlug = "interop-multi-" + UUID.randomUUID().toString().substring(0, 8);
        long namespaceId = jdbc.queryForObject("""
                INSERT INTO namespace (slug, display_name, type)
                VALUES (?, 'Interop Multi Skill', 'TEAM') RETURNING id
                """, Long.class, namespaceSlug);
        jdbc.update("""
                UPDATE github_sync_admission_policy
                SET global_high = 1000, global_low = 500, namespace_high = 100, namespace_low = 50
                WHERE id = TRUE
                """);
        long sourceId = jdbc.queryForObject("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility,
                     next_check_at)
                VALUES (?, ?, 'openai', 'skills', 'NAMESPACE_ONLY',
                        CURRENT_TIMESTAMP + INTERVAL '1 day') RETURNING id
                """, Long.class, namespaceId, repositoryId);
        UUID taskId = createRepositoryTask(namespaceId, sourceId, repositoryId);

        String workerOutput = runPublicMultiSkillWorker(taskId, false);
        assertThat(workerOutput).contains("PASS");
        if (realTree) {
            assertThat(workerOutput).contains("real Tree API: tree_calls=1");
        }
        String lastErrorCode = jdbc.queryForObject(
                "SELECT last_error_code FROM github_sync_task WHERE id = ?", String.class, taskId);
        assertThat(jdbc.queryForObject("SELECT status FROM github_sync_task WHERE id = ?",
                String.class, taskId)).as("lastErrorCode=%s", lastErrorCode)
                .isEqualTo("ACQUISITION_COMPLETE");
        assertThat(jdbc.queryForList("""
                SELECT skill_path FROM github_sync_path_result
                WHERE task_id = ? AND commit_sha = ? AND outcome = 'ACCEPTED'
                """, String.class, taskId, COMMIT)).containsExactlyInAnyOrder(
                "skills/.system/skill-creator", "skills/.curated/define-goal");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_package_ingestion
                WHERE task_id = ? AND status = 'RECEIVED'
                """, Integer.class, taskId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT sha256 FROM github_sync_package_ingestion
                WHERE task_id = ? AND skill_path = 'skills/.system/skill-creator'
                """, String.class, taskId)).isEqualTo(referenceDigest);

        ingestionTask.validatePending();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_package_ingestion
                WHERE task_id = ? AND status IN ('VALIDATED', 'READY_FOR_REVIEW')
                  AND validated_at IS NOT NULL AND content_sha256 IS NOT NULL
                """, Integer.class, taskId)).isEqualTo(2);

        publicationTask.publishValidated();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_package_ingestion
                WHERE task_id = ? AND status = 'READY_FOR_REVIEW'
                  AND published_version_id IS NOT NULL
                """, Integer.class, taskId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM review_task
                WHERE namespace_id = ? AND origin_type = 'GITHUB_SYNC' AND status = 'PENDING'
                """, Integer.class, namespaceId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM skill
                WHERE namespace_id = ? AND owner_id = 'github-sync-publisher'
                  AND visibility = 'NAMESPACE_ONLY' AND latest_version_id IS NULL
                """, Integer.class, namespaceId)).isEqualTo(2);

        Long firstVersionId = jdbc.queryForObject("""
                SELECT published_version_id FROM github_sync_package_ingestion
                WHERE task_id = ? AND skill_path = 'skills/.system/skill-creator'
                """, Long.class, taskId);
        Long secondVersionId = jdbc.queryForObject("""
                SELECT published_version_id FROM github_sync_package_ingestion
                WHERE task_id = ? AND skill_path = 'skills/.curated/define-goal'
                """, Long.class, taskId);
        assertThat(firstVersionId).isNotEqualTo(secondVersionId);
        scanTaskOutboxDispatcher.dispatch();
        awaitVersionStatus(firstVersionId, "PENDING_REVIEW");
        awaitVersionStatus(secondVersionId, "PENDING_REVIEW");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM security_audit
                WHERE skill_version_id IN (?, ?) AND is_safe = TRUE AND scanned_at IS NOT NULL
                """, Integer.class, firstVersionId, secondVersionId)).isEqualTo(2);

        jdbc.update("""
                INSERT INTO user_account (id, display_name)
                VALUES ('interop-reviewer', 'Interop Reviewer')
                ON CONFLICT (id) DO NOTHING
                """);
        Long firstReviewId = jdbc.queryForObject("""
                SELECT review.id FROM review_task review
                JOIN github_sync_package_ingestion ingestion
                  ON ingestion.id = review.github_sync_ingestion_id
                WHERE ingestion.task_id = ? AND ingestion.skill_path = 'skills/.system/skill-creator'
                """, Long.class, taskId);
        Long secondReviewId = jdbc.queryForObject("""
                SELECT review.id FROM review_task review
                JOIN github_sync_package_ingestion ingestion
                  ON ingestion.id = review.github_sync_ingestion_id
                WHERE ingestion.task_id = ? AND ingestion.skill_path = 'skills/.curated/define-goal'
                """, Long.class, taskId);
        reviewService.approveReview(firstReviewId, "interop-reviewer", "License evidence checked",
                Map.of(), Set.of("SUPER_ADMIN"));
        assertThat(jdbc.queryForObject("""
                SELECT latest_version_id FROM skill
                WHERE id = (SELECT skill_id FROM skill_version WHERE id = ?)
                """, Long.class, firstVersionId)).isEqualTo(firstVersionId);
        assertThat(jdbc.queryForObject("""
                SELECT latest_version_id FROM skill
                WHERE id = (SELECT skill_id FROM skill_version WHERE id = ?)
                """, Long.class, secondVersionId)).isNull();
        reviewService.approveReview(secondReviewId, "interop-reviewer", "License evidence checked",
                Map.of(), Set.of("SUPER_ADMIN"));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_package_ingestion
                WHERE task_id = ? AND status = 'APPROVED'
                """, Integer.class, taskId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM skill_version
                WHERE id IN (?, ?) AND status = 'PUBLISHED'
                """, Integer.class, firstVersionId, secondVersionId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT latest_version_id FROM skill
                WHERE id = (SELECT skill_id FROM skill_version WHERE id = ?)
                """, Long.class, secondVersionId)).isEqualTo(secondVersionId);
        assertThat(anonymousSkillDetail(namespaceSlug, skillSlug(firstVersionId)).statusCode())
                .isEqualTo(403);
        assertThat(anonymousSkillDetail(namespaceSlug, skillSlug(secondVersionId)).statusCode())
                .isEqualTo(403);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SKILLHUB_GITHUB_SYNC_LIVE_ALL_PACKAGES_INTEROP", matches = "1")
    void repositoryWorkerReportsEveryEligiblePublicSkillThroughJava() throws Exception {
        if (!"1".equals(System.getenv("SKILLHUB_GITHUB_SYNC_LIVE_WORKER_REAL_TREE"))) {
            throw new IllegalStateException("full repository interop requires the real GitHub Tree API");
        }
        String namespaceSlug = "interop-full-" + UUID.randomUUID().toString().substring(0, 8);
        long namespaceId = jdbc.queryForObject("""
                INSERT INTO namespace (slug, display_name, type)
                VALUES (?, 'Interop Full Repository', 'TEAM') RETURNING id
                """, Long.class, namespaceSlug);
        jdbc.update("""
                UPDATE github_sync_admission_policy
                SET global_high = 1000, global_low = 500, namespace_high = 100, namespace_low = 50
                WHERE id = TRUE
                """);
        long sourceId = jdbc.queryForObject("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility,
                     next_check_at)
                VALUES (?, 1103643573, 'openai', 'skills', 'NAMESPACE_ONLY',
                        CURRENT_TIMESTAMP + INTERVAL '1 day') RETURNING id
                """, Long.class, namespaceId);
        UUID taskId = createRepositoryTask(namespaceId, sourceId, 1103643573L);

        String workerOutput = runPublicMultiSkillWorker(taskId, true);
        assertThat(workerOutput).contains("PASS", "real Tree API: tree_calls=1");
        Map<String, Object> taskState = jdbc.queryForMap("""
                SELECT status, last_error_code FROM github_sync_task WHERE id = ?
                """, taskId);
        List<Map<String, Object>> pathReasons = jdbc.queryForList("""
                SELECT outcome, reason, count(*) AS paths FROM github_sync_path_result
                WHERE task_id = ? GROUP BY outcome, reason ORDER BY outcome, reason
                """, taskId);
        for (int recoveryAttempt = 0;
                recoveryAttempt < 2 && "RETRY_WAIT".equals(taskState.get("status"));
                recoveryAttempt++) {
            assertThat(taskState.get("last_error_code")).isEqualTo("BUDGET");
            assertThat(pathReasons).anySatisfy(reason -> {
                assertThat(reason.get("outcome")).isEqualTo("RETRYABLE_FAILURE");
                assertThat(reason.get("reason")).isEqualTo("PACKAGE_ACQUISITION_FAILED");
            });
            jdbc.update("""
                    UPDATE github_sync_task
                    SET retry_at = CURRENT_TIMESTAMP - INTERVAL '1 second',
                        due_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                    WHERE id = ? AND status = 'RETRY_WAIT'
                    """, taskId);
            assertThat(runPublicMultiSkillWorker(taskId, true))
                    .contains("PASS", "real Tree API: tree_calls=1");
            taskState = jdbc.queryForMap("""
                    SELECT status, last_error_code FROM github_sync_task WHERE id = ?
                    """, taskId);
            pathReasons = jdbc.queryForList("""
                    SELECT outcome, reason, count(*) AS paths FROM github_sync_path_result
                    WHERE task_id = ? GROUP BY outcome, reason ORDER BY outcome, reason
                    """, taskId);
        }
        assertThat(taskState.get("status"))
                .as("lastErrorCode=%s, pathReasons=%s", taskState.get("last_error_code"), pathReasons)
                .isEqualTo("PARTIAL");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_path_result
                WHERE task_id = ? AND commit_sha = ? AND outcome = 'ACCEPTED'
                """, Integer.class, taskId, COMMIT)).isEqualTo(43);
        assertThat(jdbc.queryForList("""
                SELECT skill_path FROM github_sync_path_result
                WHERE task_id = ? AND commit_sha = ? AND outcome = 'TERMINAL_FAILURE'
                  AND reason = 'PACKAGE_TOO_LARGE'
                """, String.class, taskId, COMMIT)).containsExactly(
                "skills/.curated/cloudflare-deploy");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_package_ingestion
                WHERE task_id = ? AND status = 'RECEIVED'
                """, Integer.class, taskId)).isEqualTo(43);
        assertThat(jdbc.queryForObject("""
                SELECT count(DISTINCT skill_path) FROM github_sync_package_ingestion
                WHERE task_id = ?
                """, Integer.class, taskId)).isEqualTo(43);

        for (int batch = 0; batch < 6; batch++) {
            ingestionTask.validatePending();
        }
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_package_ingestion
                WHERE task_id = ? AND status = 'RECEIVED'
                """, Integer.class, taskId)).isZero();
        for (int batch = 0; batch < 11; batch++) {
            publicationTask.publishValidated();
        }
        List<Map<String, Object>> ingestionStatuses = jdbc.queryForList("""
                SELECT status, last_error_code, count(*) AS packages
                FROM github_sync_package_ingestion
                WHERE task_id = ? GROUP BY status, last_error_code ORDER BY status, last_error_code
                """, taskId);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_package_ingestion
                WHERE task_id = ? AND status = 'VALIDATED'
                """, Integer.class, taskId)).as("ingestion statuses=%s", ingestionStatuses).isZero();
        Integer pendingReviews = jdbc.queryForObject("""
                SELECT count(*) FROM review_task
                WHERE namespace_id = ? AND origin_type = 'GITHUB_SYNC' AND status = 'PENDING'
                """, Integer.class, namespaceId);
        Integer readyForReview = jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_package_ingestion
                WHERE task_id = ? AND status = 'READY_FOR_REVIEW'
                """, Integer.class, taskId);
        assertThat(pendingReviews).as("ingestion statuses=%s", ingestionStatuses).isEqualTo(42);
        assertThat(pendingReviews).isEqualTo(readyForReview);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_package_ingestion
                WHERE task_id = ? AND status = 'BLOCKED' AND last_error_code = 'SLUG_CONFLICT'
                  AND target_skill_slug IS NOT NULL
                """, Integer.class, taskId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_source_skill_binding
                WHERE namespace_id = ? AND source_id = ? AND upstream_presence = 'PRESENT'
                """, Integer.class, namespaceId, sourceId)).isEqualTo(42);
        System.out.printf("Full public repository ingestion: statuses=%s pendingReviews=%d%n",
                ingestionStatuses, pendingReviews);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM skill
                WHERE namespace_id = ? AND latest_version_id IS NOT NULL
                """, Integer.class, namespaceId)).isZero();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SKILLHUB_GITHUB_SYNC_LIVE_INTERRUPT_INTEROP", matches = "1")
    void repositoryWorkerResumesAfterSigtermBetweenReceiptAndAcknowledgement() throws Exception {
        long repositoryId = "1".equals(System.getenv("SKILLHUB_GITHUB_SYNC_LIVE_WORKER_REAL_TREE"))
                ? 1103643573L : 987654350L;
        String namespaceSlug = "interop-interrupted-" + UUID.randomUUID().toString().substring(0, 8);
        long namespaceId = jdbc.queryForObject("""
                INSERT INTO namespace (slug, display_name, type)
                VALUES (?, 'Interrupted Worker', 'TEAM') RETURNING id
                """, Long.class, namespaceSlug);
        jdbc.update("""
                UPDATE github_sync_admission_policy
                SET global_high = 1000, global_low = 500, namespace_high = 100, namespace_low = 50
                WHERE id = TRUE
                """);
        long sourceId = jdbc.queryForObject("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility,
                     next_check_at)
                VALUES (?, ?, 'openai', 'skills', 'NAMESPACE_ONLY',
                        CURRENT_TIMESTAMP + INTERVAL '1 day') RETURNING id
                """, Long.class, namespaceId, repositoryId);
        UUID taskId = createRepositoryTask(namespaceId, sourceId, repositoryId);
        Path tempRoot = Files.createTempDirectory("skillhub-github-interrupted-");
        try {
            ProcessBuilder builder = publicMultiSkillWorkerBuilder(taskId, false);
            builder.environment().put("SKILLHUB_INTEROP_WORKER_INTERRUPT_AFTER_RECEIPT", "1");
            builder.environment().put("SKILLHUB_INTEROP_WORKER_TEMP_ROOT", tempRoot.toString());
            Process process = builder.start();
            if (!process.waitFor(180, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("Interrupted Go worker timed out");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(process.exitValue()).as("Interrupted Go worker: %s", output).isZero();
            assertThat(output).contains("interrupted after Java package receipt", "PASS");
            try (var remaining = Files.list(tempRoot)) {
                assertThat(remaining.toList()).isEmpty();
            }
            assertThat(jdbc.queryForObject("SELECT status FROM github_sync_task WHERE id = ?",
                    String.class, taskId)).isEqualTo("LEASED");
            UUID firstIngestionId = jdbc.queryForObject("""
                    SELECT id FROM github_sync_package_ingestion WHERE task_id = ?
                    """, UUID.class, taskId);
            String firstPath = jdbc.queryForObject("""
                    SELECT skill_path FROM github_sync_package_ingestion WHERE id = ?
                    """, String.class, firstIngestionId);
            assertThat(jdbc.queryForObject("""
                    SELECT ingestion_id FROM github_sync_path_result
                    WHERE task_id = ? AND skill_path = ? AND outcome = 'ACCEPTED'
                    """, UUID.class, taskId, firstPath)).isEqualTo(firstIngestionId);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM github_sync_package_admission
                    WHERE task_id = ? AND skill_path = ?
                    """, Integer.class, taskId, firstPath)).isEqualTo(1);

            jdbc.update("""
                    UPDATE github_sync_task
                    SET lease_expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                    WHERE id = ? AND status = 'LEASED'
                    """, taskId);
            assertThat(runPublicMultiSkillWorker(taskId, false)).contains("PASS");
            assertThat(jdbc.queryForObject("SELECT status FROM github_sync_task WHERE id = ?",
                    String.class, taskId)).isEqualTo("ACQUISITION_COMPLETE");
            assertThat(jdbc.queryForObject("SELECT lease_generation FROM github_sync_task WHERE id = ?",
                    Long.class, taskId)).isEqualTo(2L);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM github_sync_path_result
                    WHERE task_id = ? AND outcome = 'ACCEPTED'
                    """, Integer.class, taskId)).isEqualTo(2);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM github_sync_package_ingestion WHERE task_id = ?
                    """, Integer.class, taskId)).isEqualTo(2);
            assertThat(jdbc.queryForObject("""
                    SELECT id FROM github_sync_package_ingestion
                    WHERE task_id = ? AND skill_path = ?
                    """, UUID.class, taskId, firstPath)).isEqualTo(firstIngestionId);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM github_sync_package_admission
                    WHERE task_id = ? AND skill_path = ?
                    """, Integer.class, taskId, firstPath)).isEqualTo(1);
        } finally {
            FileSystemUtils.deleteRecursively(tempRoot);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SKILLHUB_GITHUB_SYNC_INTEROP_IMAGE", matches = ".+")
    void serviceContainerResumesPublicRepositoryOnSmallTmpfs() throws Exception {
        String token = publicReadTokenForInterop();
        UUID credentialId = createFixtureCredential("Container public read", token);
        try {
            String namespaceSlug = "interop-container-" + UUID.randomUUID().toString().substring(0, 8);
            long namespaceId = jdbc.queryForObject("""
                    INSERT INTO namespace (slug, display_name, type)
                    VALUES (?, 'Container Interop', 'TEAM') RETURNING id
                    """, Long.class, namespaceSlug);
            jdbc.update("""
                    UPDATE github_sync_admission_policy
                    SET global_high = 1000, global_low = 500, namespace_high = 100, namespace_low = 50
                    WHERE id = TRUE
                    """);
            long sourceId = jdbc.queryForObject("""
                    INSERT INTO github_source_binding
                        (namespace_id, repository_id, owner_login, repository_name, visibility,
                         next_check_at)
                    VALUES (?, 1103643573, 'openai', 'skills', 'NAMESPACE_ONLY',
                            CURRENT_TIMESTAMP + INTERVAL '1 day') RETURNING id
                    """, Long.class, namespaceId);
            UUID taskId = createRepositoryTask(namespaceId, sourceId, 1103643573L);
            String image = System.getenv("SKILLHUB_GITHUB_SYNC_INTEROP_IMAGE");
            GenericContainer<?> first = publicRepositoryServiceContainer(image);
            try {
                first.start();
                Instant firstDeadline = Instant.now().plusSeconds(120);
                while (Instant.now().isBefore(firstDeadline) && jdbc.queryForObject("""
                        SELECT count(*) FROM github_sync_package_ingestion WHERE task_id = ?
                        """, Integer.class, taskId) == 0) {
                    assertThat(first.isRunning()).as("Go container exited before the first package receipt")
                            .isTrue();
                    Thread.sleep(50);
                }
                assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM github_sync_package_ingestion WHERE task_id = ?
                        """, Integer.class, taskId)).isBetween(1, 42);
            } finally {
                first.stop();
            }
            assertThat(jdbc.queryForObject("SELECT status FROM github_sync_task WHERE id = ?",
                    String.class, taskId)).isEqualTo("LEASED");
            UUID firstIngestionId = jdbc.queryForObject("""
                    SELECT id FROM github_sync_package_ingestion
                    WHERE task_id = ? ORDER BY created_at, id LIMIT 1
                    """, UUID.class, taskId);
            String firstPath = jdbc.queryForObject("""
                    SELECT skill_path FROM github_sync_package_ingestion WHERE id = ?
                    """, String.class, firstIngestionId);
            jdbc.update("""
                    UPDATE github_sync_task
                    SET lease_expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                    WHERE id = ? AND status = 'LEASED'
                    """, taskId);
            try (GenericContainer<?> second = publicRepositoryServiceContainer(image)) {
                second.start();
                Instant deadline = Instant.now().plusSeconds(600);
                int recoveryAttempts = 0;
                String status = "";
                while (Instant.now().isBefore(deadline)) {
                    Map<String, Object> state = jdbc.queryForMap("""
                            SELECT status, last_error_code FROM github_sync_task WHERE id = ?
                            """, taskId);
                    status = (String) state.get("status");
                    if ("PARTIAL".equals(status)) {
                        break;
                    }
                    if ("RETRY_WAIT".equals(status)) {
                        assertThat(state.get("last_error_code")).isEqualTo("BUDGET");
                        assertThat(recoveryAttempts++).isLessThan(2);
                        jdbc.update("""
                                UPDATE github_sync_task
                                SET retry_at = CURRENT_TIMESTAMP - INTERVAL '1 second',
                                    due_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                                WHERE id = ? AND status = 'RETRY_WAIT'
                                """, taskId);
                    }
                    assertThat(second.isRunning()).as("Go container exited before completion")
                            .isTrue();
                    Thread.sleep(200);
                }
                assertThat(status).isEqualTo("PARTIAL");
                var processes = second.execInContainer("ps", "-o", "pid,ppid,stat,comm");
                assertThat(processes.getExitCode()).isZero();
                assertThat(processes.getStdout()).contains("tini");
                long zombies = processes.getStdout().lines().skip(1)
                        .filter(line -> line.trim().matches("[0-9]+\\s+[0-9]+\\s+Z\\S*\\s+.*"))
                        .count();
                assertThat(zombies).as("orphaned Git subprocesses").isZero();
            }
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM github_sync_path_result
                    WHERE task_id = ? AND outcome = 'ACCEPTED'
                    """, Integer.class, taskId)).isEqualTo(43);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM github_sync_package_ingestion WHERE task_id = ?
                    """, Integer.class, taskId)).isEqualTo(43);
            assertThat(jdbc.queryForObject("""
                    SELECT id FROM github_sync_package_ingestion
                    WHERE task_id = ? AND skill_path = ?
                    """, UUID.class, taskId, firstPath)).isEqualTo(firstIngestionId);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM github_sync_package_admission
                    WHERE task_id = ? AND skill_path = ?
                    """, Integer.class, taskId, firstPath)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT lease_generation FROM github_sync_task WHERE id = ?",
                    Long.class, taskId)).isGreaterThanOrEqualTo(2L);
        } finally {
            systemCredentials.setEnabled(credentialId, false);
        }
    }

    private GenericContainer<?> publicRepositoryServiceContainer(String image) {
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse(image))
                .withNetworkMode("host")
                .withTmpFs(Map.of("/var/lib/skillhub-github-sync",
                        "rw,size=100663296,mode=1777"))
                .withEnv("SKILLHUB_GITHUB_SYNC_JAVA_URL", baseUrl() + "/internal/github-sync/v1")
                .withEnv("SKILLHUB_GITHUB_SYNC_SERVICE_TOKEN", SERVICE_TOKEN)
                .withEnv("SKILLHUB_GITHUB_SYNC_HEALTH_ADDR", "127.0.0.1:8090")
                .withEnv("SKILLHUB_GITHUB_SYNC_POLL_INTERVAL", "1s")
                .withEnv("SKILLHUB_GITHUB_SYNC_MAX_WORKSPACE_BYTES", "67108864")
                .withEnv("SKILLHUB_GITHUB_SYNC_TOTAL_WORKSPACE_BYTES", "67108864")
                .withEnv("SKILLHUB_GITHUB_SYNC_MIN_FREE_BYTES", "16777216");
        if (S3_MODE) {
            container.withEnv("SKILLHUB_GITHUB_SYNC_ALLOWED_S3_HOSTS",
                    URI.create(s3Endpoint).getAuthority());
        }
        for (String proxy : List.of("HTTP_PROXY", "HTTPS_PROXY", "NO_PROXY")) {
            String value = System.getenv(proxy);
            if (value != null && !value.isBlank()) {
                container.withEnv(proxy, value);
            }
        }
        return container;
    }

    private String publicReadTokenForInterop() throws Exception {
        String token = System.getenv("SKILLHUB_GITHUB_SYNC_LIVE_TOKEN");
        if (token != null && !token.isBlank()) {
            return token.trim();
        }
        Process process = new ProcessBuilder("gh", "auth", "token").start();
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Public repository container test needs GitHub read access");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Public repository container test needs GitHub read access");
        }
        token = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (token.isBlank()) {
            throw new IllegalStateException("Public repository container test has no GitHub read token");
        }
        return token;
    }

    private String runPublicMultiSkillWorker(UUID taskId, boolean allTree) throws Exception {
        Process process = publicMultiSkillWorkerBuilder(taskId, allTree).start();
        if (!process.waitFor(allTree ? 600 : 180, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Go public multi-Skill worker timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as("Go public multi-Skill worker: %s", output).isZero();
        return output;
    }

    private ProcessBuilder publicMultiSkillWorkerBuilder(UUID taskId, boolean allTree) {
        ProcessBuilder builder = new ProcessBuilder("go", "test", "./internal/worker",
                "-run", "^TestInteropProcessRepositoryPublicMultiSkill$", "-count=1", "-v");
        builder.directory(findGoModule().toFile()).redirectErrorStream(true);
        builder.environment().put("SKILLHUB_INTEROP_WORKER_PUBLIC_MULTI", "1");
        builder.environment().put("SKILLHUB_INTEROP_JAVA_URL", baseUrl() + "/internal/github-sync/v1");
        builder.environment().put("SKILLHUB_INTEROP_SERVICE_TOKEN", SERVICE_TOKEN);
        builder.environment().put("SKILLHUB_INTEROP_TASK_ID", taskId.toString());
        if ("1".equals(System.getenv("SKILLHUB_GITHUB_SYNC_LIVE_WORKER_REAL_TREE"))) {
            builder.environment().put("SKILLHUB_INTEROP_WORKER_REAL_TREE", "1");
        }
        if (allTree) {
            builder.environment().put("SKILLHUB_INTEROP_WORKER_ALL_TREE", "1");
        }
        if (S3_MODE) {
            builder.environment().put("SKILLHUB_INTEROP_ALLOWED_S3_HOST",
                    URI.create(s3Endpoint).getAuthority());
        }
        return builder;
    }

    @Test
    void repositoryWorkerReleasesAdmissionAndDefersWhenPackageCannotFit() throws Exception {
        long namespaceId = jdbc.queryForObject("""
                SELECT id FROM namespace WHERE slug = 'github' AND type = 'CATALOG'
                """, Long.class);
        jdbc.update("""
                UPDATE github_sync_admission_policy
                SET global_high = 1000, global_low = 500, namespace_high = 100, namespace_low = 50
                WHERE id = TRUE
                """);
        long sourceId = jdbc.queryForObject("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility,
                     next_check_at)
                VALUES (?, 987654330, 'openai', 'skills', 'PUBLIC',
                        CURRENT_TIMESTAMP + INTERVAL '1 day') RETURNING id
                """, Long.class, namespaceId);
        UUID taskId = createRepositoryTask(namespaceId, sourceId, 987654330, "c".repeat(40));

        assertThat(runCapacityWorker(taskId)).contains("PASS");
        assertThat(jdbc.queryForObject("""
                SELECT status FROM github_sync_task WHERE id = ?
                """, String.class, taskId)).isEqualTo("RETRY_WAIT");
        assertThat(jdbc.queryForObject("""
                SELECT last_error_code FROM github_sync_task WHERE id = ?
                """, String.class, taskId)).isEqualTo("CAPACITY");
        assertThat(jdbc.queryForObject("""
                SELECT retry_at FROM github_sync_task WHERE id = ?
                """, Timestamp.class, taskId).toInstant()).isAfter(Instant.now().plus(Duration.ofHours(23)));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_package_admission
                WHERE task_id = ? AND status = 'RELEASED'
                """, Integer.class, taskId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_package_ingestion WHERE task_id = ?
                """, Integer.class, taskId)).isZero();
    }

    private String runCapacityWorker(UUID taskId) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("go", "test", "./internal/worker",
                "-run", "^TestInteropProcessRepositoryCapacity$", "-count=1", "-v");
        builder.directory(findGoModule().toFile()).redirectErrorStream(true);
        builder.environment().put("SKILLHUB_INTEROP_WORKER_CAPACITY", "1");
        builder.environment().put("SKILLHUB_INTEROP_JAVA_URL", baseUrl() + "/internal/github-sync/v1");
        builder.environment().put("SKILLHUB_INTEROP_SERVICE_TOKEN", SERVICE_TOKEN);
        builder.environment().put("SKILLHUB_INTEROP_TASK_ID", taskId.toString());
        Process process = builder.start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Go capacity worker timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as("Go capacity worker: %s", output).isZero();
        return output;
    }

    @Test
    void highWatermarkMakesGoCheckHeadWithoutAcquiringPackages() throws Exception {
        long namespaceId = jdbc.queryForObject("""
                SELECT id FROM namespace WHERE slug = 'github' AND type = 'CATALOG'
                """, Long.class);
        Map<String, Object> previousPolicy = jdbc.queryForMap("""
                SELECT global_high, global_low, namespace_high, namespace_low, global_paused
                FROM github_sync_admission_policy WHERE id = TRUE
                """);
        UUID blockerRun = UUID.randomUUID();
        UUID blockerTask = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO github_sync_run (id, namespace_id, kind)
                VALUES (?, ?, 'REPOSITORY_SYNC')
                """, blockerRun, namespaceId);
        jdbc.update("""
                INSERT INTO github_sync_task
                    (id, run_id, namespace_id, kind, mode, status, dedupe_key, snapshot_json,
                     lease_generation, lease_token_hash, lease_expires_at, leased_by)
                VALUES (?, ?, ?, 'REPOSITORY_SYNC', 'FULL', 'LEASED', ?,
                        jsonb_build_object('repositoryId', 987654331), 1, ?,
                        CURRENT_TIMESTAMP + INTERVAL '5 minutes', 'capacity-blocker')
                """, blockerTask, blockerRun, namespaceId,
                "repository:987654331", "b".repeat(64));
        UUID blockerAdmission = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO github_sync_package_admission
                    (id, task_id, namespace_id, repository_id, commit_sha, skill_path,
                     lease_generation)
                VALUES (?, ?, ?, 987654331, ?, 'skills/blocker', 1)
                """, blockerAdmission, blockerTask, namespaceId, "c".repeat(40));
        long sourceId = jdbc.queryForObject("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility,
                     next_check_at)
                VALUES (?, 987654332, 'old-owner', 'old-name', 'PUBLIC',
                        CURRENT_TIMESTAMP + INTERVAL '1 day') RETURNING id
                """, Long.class, namespaceId);
        UUID taskId = createRepositoryTask(namespaceId, sourceId, 987654332, "c".repeat(40));
        jdbc.update("""
                UPDATE github_sync_task
                SET snapshot_json = snapshot_json || jsonb_build_object(
                    'owner', 'old-owner', 'name', 'old-name')
                WHERE id = ?
                """, taskId);

        jdbc.update("""
                UPDATE github_sync_admission_policy
                SET global_high = 1, global_low = 0, namespace_high = 100, namespace_low = 50,
                    global_paused = FALSE WHERE id = TRUE
                """);
        try {
            assertThat(runHeadOnlyWorker(taskId)).contains("PASS");
            assertThat(jdbc.queryForObject("""
                    SELECT mode FROM github_sync_task WHERE id = ?
                    """, String.class, taskId)).isEqualTo("HEAD_ONLY");
            assertThat(jdbc.queryForObject("""
                    SELECT status FROM github_sync_task WHERE id = ?
                    """, String.class, taskId)).isEqualTo("ACQUISITION_COMPLETE");
            assertThat(jdbc.queryForObject("""
                    SELECT last_observed_commit FROM github_source_binding WHERE id = ?
                    """, String.class, sourceId)).isEqualTo("c".repeat(40));
            assertThat(jdbc.queryForObject("""
                    SELECT last_fully_processed_commit FROM github_source_binding WHERE id = ?
                    """, String.class, sourceId)).isNull();
            assertThat(jdbc.queryForObject("""
                    SELECT owner_login || '/' || repository_name
                    FROM github_source_binding WHERE id = ?
                    """, String.class, sourceId)).isEqualTo("new-owner/new-name");
            assertThat(jdbc.queryForObject("""
                    SELECT concat(snapshot_json ->> 'owner', '/', snapshot_json ->> 'name')
                    FROM github_sync_task WHERE id = ?
                    """, String.class, taskId)).isEqualTo("new-owner/new-name");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM github_sync_package_admission WHERE task_id = ?
                    """, Integer.class, taskId)).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT status FROM github_sync_package_admission WHERE id = ?
                    """, String.class, blockerAdmission)).isEqualTo("ACTIVE");
            jdbc.update("UPDATE github_sync_package_admission SET status = 'RELEASED' WHERE id = ?", blockerAdmission);
            assertThat(sourceTasks.enqueueDue(Instant.now(), 10)).isPositive();
            UUID resumedTask = jdbc.queryForObject("SELECT id FROM github_sync_task WHERE source_id = ? AND status = 'QUEUED'",
                    UUID.class, sourceId);
            assertThat(jdbc.queryForObject("SELECT mode FROM github_sync_task WHERE id = ?",
                    String.class, resumedTask)).isEqualTo("FULL");
            assertThat(jdbc.queryForObject("SELECT fixed_commit_sha FROM github_sync_task WHERE id = ?",
                    String.class, resumedTask)).isNull();
            // The latest HEAD is obtained by Go; the old high-watermark observation must not pin this task.
            String freshCommit = "d".repeat(40);
            Instant resumedAt = Instant.now();
            String proof = "e".repeat(64);
            var resumedLease = syncTasks.claim(GithubSyncTaskKind.REPOSITORY_SYNC,
                    "resumed-head-worker", proof, resumedAt, resumedAt.plusSeconds(300),
                    java.util.List.of("platform-pause/v1")).orElseThrow();
            assertThat(resumedLease.taskId()).isEqualTo(resumedTask);
            var admitted = syncTasks.admitPackage(resumedTask, resumedLease.leaseGeneration(), proof,
                    freshCommit, "skills/fresh", resumedAt);
            assertThat(admitted.status()).isEqualTo(GithubPackageAdmissionResult.Status.ADMITTED);
            assertThat(jdbc.queryForObject("SELECT fixed_commit_sha FROM github_sync_task WHERE id = ?",
                    String.class, resumedTask)).isEqualTo(freshCommit);
            jdbc.update("UPDATE github_sync_package_admission SET status = 'RELEASED' WHERE task_id = ?", resumedTask);
            jdbc.update("UPDATE github_sync_task SET status = 'FAILED', lease_token_hash = NULL, lease_expires_at = NULL, leased_by = NULL WHERE id = ?", resumedTask);
        } finally {
            jdbc.update("""
                    UPDATE github_sync_admission_policy
                    SET global_high = ?, global_low = ?, namespace_high = ?, namespace_low = ?,
                        global_paused = ? WHERE id = TRUE
                    """, previousPolicy.get("global_high"), previousPolicy.get("global_low"),
                    previousPolicy.get("namespace_high"), previousPolicy.get("namespace_low"),
                    previousPolicy.get("global_paused"));
            jdbc.update("UPDATE github_sync_package_admission SET status = 'RELEASED' WHERE id = ?",
                    blockerAdmission);
            jdbc.update("""
                    UPDATE github_sync_task
                    SET status = 'FAILED', lease_token_hash = NULL, lease_expires_at = NULL,
                        leased_by = NULL WHERE id = ?
                    """, blockerTask);
            jdbc.update("UPDATE github_source_binding SET status = 'PAUSED' WHERE id = ?", sourceId);
        }
    }

    @Test
    void renamedCandidateWithoutSkillStaysUnboundAfterGoValidation() throws Exception {
        long namespaceId = jdbc.queryForObject("""
                INSERT INTO namespace (slug, display_name, type)
                VALUES (?, 'Candidate Interop', 'TEAM') RETURNING id
                """, Long.class, "candidate-interop-" + UUID.randomUUID().toString().substring(0, 8));
        jdbc.update("""
                UPDATE github_sync_admission_policy
                SET global_high = 1000, global_low = 500, namespace_high = 100, namespace_low = 50
                WHERE id = TRUE
                """);
        long candidateId = jdbc.queryForObject("""
                INSERT INTO github_discovery_candidate
                    (namespace_id, repository_id, owner_login, repository_name, next_validation_at)
                VALUES (?, 987654333, 'old-owner', 'old-name', CURRENT_TIMESTAMP - INTERVAL '1 second')
                RETURNING id
                """, Long.class, namespaceId);
        assertThat(candidateTasks.enqueueDue(Instant.now(), 10)).isEqualTo(1);
        UUID taskId = jdbc.queryForObject("""
                SELECT validation_task_id FROM github_discovery_candidate WHERE id = ?
                """, UUID.class, candidateId);

        assertThat(runEmptyCandidateWorker(taskId)).contains("PASS");
        assertThat(jdbc.queryForObject("""
                SELECT status FROM github_sync_task WHERE id = ?
                """, String.class, taskId)).isEqualTo("ACQUISITION_COMPLETE");
        assertThat(jdbc.queryForObject("""
                SELECT owner_login || '/' || repository_name
                FROM github_discovery_candidate WHERE id = ?
                """, String.class, candidateId)).isEqualTo("new-owner/new-name");
        assertThat(jdbc.queryForObject("""
                SELECT concat(snapshot_json ->> 'owner', '/', snapshot_json ->> 'name')
                FROM github_sync_task WHERE id = ?
                """, String.class, taskId)).isEqualTo("new-owner/new-name");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_source_binding
                WHERE namespace_id = ? AND repository_id = 987654333
                """, Integer.class, namespaceId)).isZero();
        assertThat(candidateTasks.reconcileCompleted(Instant.now(), 10)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT status || '/' || last_error_code
                FROM github_discovery_candidate WHERE id = ?
                """, String.class, candidateId)).isEqualTo("REJECTED/NO_VALID_SKILL");
        assertThat(jdbc.queryForObject("""
                SELECT next_validation_at FROM github_discovery_candidate WHERE id = ?
                """, Timestamp.class, candidateId).toInstant()).isAfter(Instant.now().plus(Duration.ofDays(6)));
    }

    private String runEmptyCandidateWorker(UUID taskId) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("go", "test", "./internal/worker",
                "-run", "^TestInteropProcessRepositoryRenamedCandidateWithoutSkill$", "-count=1", "-v");
        builder.directory(findGoModule().toFile()).redirectErrorStream(true);
        builder.environment().put("SKILLHUB_INTEROP_WORKER_EMPTY_CANDIDATE", "1");
        builder.environment().put("SKILLHUB_INTEROP_JAVA_URL", baseUrl() + "/internal/github-sync/v1");
        builder.environment().put("SKILLHUB_INTEROP_SERVICE_TOKEN", SERVICE_TOKEN);
        builder.environment().put("SKILLHUB_INTEROP_TASK_ID", taskId.toString());
        Process process = builder.start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Go candidate worker timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as("Go candidate worker: %s", output).isZero();
        return output;
    }

    private String runHeadOnlyWorker(UUID taskId) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("go", "test", "./internal/worker",
                "-run", "^TestInteropProcessRepositoryHeadOnly$", "-count=1", "-v");
        builder.directory(findGoModule().toFile()).redirectErrorStream(true);
        builder.environment().put("SKILLHUB_INTEROP_WORKER_HEAD_ONLY", "1");
        builder.environment().put("SKILLHUB_INTEROP_JAVA_URL", baseUrl() + "/internal/github-sync/v1");
        builder.environment().put("SKILLHUB_INTEROP_SERVICE_TOKEN", SERVICE_TOKEN);
        builder.environment().put("SKILLHUB_INTEROP_TASK_ID", taskId.toString());
        Process process = builder.start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Go HEAD_ONLY worker timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as("Go HEAD_ONLY worker: %s", output).isZero();
        return output;
    }

    @Test
    void separateGoProcessesResumeDiscoveryCheckpointAfterLeaseExpiry() throws Exception {
        long namespaceId = jdbc.queryForObject("""
                SELECT id FROM namespace WHERE slug = 'github' AND type = 'CATALOG'
                """, Long.class);
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO github_sync_run (id, namespace_id, kind)
                VALUES (?, ?, 'DISCOVERY')
                """, runId, namespaceId);
        jdbc.update("""
                INSERT INTO github_sync_task
                    (id, run_id, namespace_id, kind, dedupe_key, priority, snapshot_json)
                VALUES (?, ?, ?, 'DISCOVERY', ?, 100,
                    jsonb_build_object('entryType', 'CURATED_OWNER', 'owner', 'interop-owner',
                        'ruleVersion', 1, 'candidateBudget', 10))
                """, taskId, runId, namespaceId, "discovery-interop:" + taskId);

        int javaPort;
        try (JavaApplicationProcess first = startJavaApplication(3_600_000)) {
            javaPort = first.port();
            assertThat(runDiscoveryClient(taskId, "report", null, null,
                    "http://127.0.0.1:" + javaPort)).contains("REPORTED");
            // The restart exercise must not make this discovery lead due for another test's
            // repository claim when the second application runs its startup scheduler tick.
            assertThat(jdbc.update("""
                    UPDATE github_discovery_candidate
                    SET next_validation_at = CURRENT_TIMESTAMP + INTERVAL '1 day'
                    WHERE namespace_id = ? AND repository_id = 424242
                    """, namespaceId)).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("""
                SELECT discovery_checkpoint FROM github_sync_task WHERE id = ?
                """, String.class, taskId)).isEqualTo("interop-discovery-checkpoint");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_discovery_candidate
                WHERE namespace_id = ? AND repository_id = 424242
                """, Integer.class, namespaceId)).isEqualTo(1);
        jdbc.update("""
                UPDATE github_sync_task SET lease_expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE id = ?
                """, taskId);

        try (JavaApplicationProcess restarted = startJavaApplication(3_600_000, javaPort)) {
            assertThat(runDiscoveryClient(taskId, "resume", null, null,
                    "http://127.0.0.1:" + restarted.port())).contains("COMPLETED");
        }
        assertThat(jdbc.queryForObject("""
                SELECT status FROM github_sync_task WHERE id = ?
                """, String.class, taskId)).isEqualTo("ACQUISITION_COMPLETE");
        assertThat(jdbc.queryForObject("""
                SELECT lease_generation FROM github_sync_task WHERE id = ?
                """, Long.class, taskId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_discovery_batch WHERE task_id = ?
                """, Integer.class, taskId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_discovery_task_hit WHERE task_id = ?
                """, Integer.class, taskId)).isEqualTo(1);
    }

    @Test
    void searchPreviewFlowsThroughJavaGoJavaWithoutCreatingSources() throws Exception {
        long namespaceId = jdbc.queryForObject("""
                SELECT id FROM namespace WHERE slug = 'github' AND type = 'CATALOG'
                """, Long.class);
        int candidatesBefore = jdbc.queryForObject("""
                SELECT count(*) FROM github_discovery_candidate WHERE namespace_id = ?
                """, Integer.class, namespaceId);
        int sourcesBefore = jdbc.queryForObject("""
                SELECT count(*) FROM github_source_binding WHERE namespace_id = ?
                """, Integer.class, namespaceId);
        var request = new GithubSearchRuleRequest("Public skills", List.of(
                new GithubSearchSelectorRequest("TOPIC", "agent-skills", "EXTENSION"),
                new GithubSearchSelectorRequest("KEYWORD", "agent skills", "CORE")), null);

        UUID successId = searchPreviews.queue(request, "interop-admin").id();
        assertThat(runSearchPreviewWorker(successId, "success")).contains("PASS");
        var completed = searchPreviews.get(successId);
        assertThat(completed.status()).isEqualTo("ACQUISITION_COMPLETE");
        assertThat(completed.result().path("selectors").size()).isEqualTo(2);
        assertThat(completed.result().path("selectors").get(0).path("kind").asText())
                .isEqualTo("KEYWORD");
        assertThat(completed.result().path("selectors").get(0).path("totalCount").asInt())
                .isEqualTo(1001);
        assertThat(completed.result().path("selectors").get(0).path("incompleteResults").asBoolean())
                .isTrue();
        assertThat(completed.result().path("selectors").get(0).path("limitReached").asBoolean())
                .isTrue();
        assertThat(completed.result().path("selectors").get(0).path("samples").size()).isEqualTo(5);

        UUID limitedId = searchPreviews.queue(request, "interop-admin").id();
        assertThat(runSearchPreviewWorker(limitedId, "limit")).contains("PASS");
        var deferred = searchPreviews.get(limitedId);
        assertThat(deferred.status()).isEqualTo("RETRY_WAIT");
        assertThat(deferred.errorCode()).isEqualTo("GITHUB_RATE_LIMITED");
        assertThat(deferred.retryAt()).isAfter(Instant.now());
        assertThat(deferred.result()).isNull();

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_discovery_batch
                WHERE task_id IN (?, ?)
                """, Integer.class, successId, limitedId)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_discovery_candidate WHERE namespace_id = ?
                """, Integer.class, namespaceId)).isEqualTo(candidatesBefore);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_source_binding WHERE namespace_id = ?
                """, Integer.class, namespaceId)).isEqualTo(sourcesBefore);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SKILLHUB_GITHUB_SYNC_LIVE_SEARCH", matches = "1")
    void publicGithubTwoSelectorPreviewCompletesWithoutCreatingSources() throws Exception {
        long namespaceId = jdbc.queryForObject("""
                SELECT id FROM namespace WHERE slug = 'github' AND type = 'CATALOG'
                """, Long.class);
        int candidatesBefore = jdbc.queryForObject("""
                SELECT count(*) FROM github_discovery_candidate WHERE namespace_id = ?
                """, Integer.class, namespaceId);
        int sourcesBefore = jdbc.queryForObject("""
                SELECT count(*) FROM github_source_binding WHERE namespace_id = ?
                """, Integer.class, namespaceId);
        var request = new GithubSearchRuleRequest("Public skills", List.of(
                new GithubSearchSelectorRequest("TOPIC", "agent-skills", "EXTENSION"),
                new GithubSearchSelectorRequest("KEYWORD", "agent skills", "CORE")), null);
        UUID taskId = searchPreviews.queue(request, "interop-admin").id();

        assertThat(runSearchPreviewWorker(taskId, "live", null, "")).contains("PASS");
        var preview = searchPreviews.get(taskId);
        assertThat(preview.status()).isEqualTo("ACQUISITION_COMPLETE");
        assertThat(preview.result().path("selectors").size()).isEqualTo(2);
        assertThat(preview.result().path("selectors").get(0).path("kind").asText())
                .isEqualTo("KEYWORD");
        assertThat(preview.result().path("selectors").get(1).path("kind").asText())
                .isEqualTo("TOPIC");
        assertThat(preview.result().path("selectors").get(0).path("totalCount").asInt())
                .isGreaterThanOrEqualTo(0);
        assertThat(preview.result().path("selectors").get(1).path("totalCount").asInt())
                .isGreaterThanOrEqualTo(0);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_discovery_batch WHERE task_id = ?
                """, Integer.class, taskId)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_discovery_candidate WHERE namespace_id = ?
                """, Integer.class, namespaceId)).isEqualTo(candidatesBefore);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_source_binding WHERE namespace_id = ?
                """, Integer.class, namespaceId)).isEqualTo(sourcesBefore);
    }

    @Test
    void curatedPublicRepositoryBecomesSourceAndQueuesRepositorySync() throws Exception {
        var entry = discoveryEntries.createCuratedRepository("openai", "skills");
        assertThat(discoveryEntries.enqueueDue(Instant.now(), 10)).isEqualTo(1);
        UUID taskId = jdbc.queryForObject("""
                SELECT id FROM github_sync_task WHERE dedupe_key = ?
                """, UUID.class, "discovery:" + entry.id());
        assertThat(runCuratedRepositoryWorker(taskId)).contains("PASS");
        assertThat(jdbc.queryForObject("""
                SELECT status FROM github_sync_task WHERE id = ?
                """, String.class, taskId)).isEqualTo("ACQUISITION_COMPLETE");

        long sourceId = jdbc.queryForObject("""
                SELECT source.id FROM github_source_binding source
                JOIN github_source_origin origin ON origin.source_id = source.id
                WHERE origin.origin_type = 'CURATED_REPOSITORY' AND origin.origin_key = ?
                """, Long.class, entry.id().toString());
        assertThat(jdbc.queryForObject("""
                SELECT repository_id FROM github_source_binding WHERE id = ?
                """, Long.class, sourceId)).isPositive();
        Map<String, Object> previousPolicy = jdbc.queryForMap("""
                SELECT global_high, global_low, namespace_high, namespace_low
                FROM github_sync_admission_policy WHERE id = TRUE
                """);
        jdbc.update("""
                UPDATE github_sync_admission_policy
                SET global_high = 1000, global_low = 500, namespace_high = 100, namespace_low = 50
                WHERE id = TRUE
                """);
        try {
            assertThat(sourceTasks.enqueueDue(Instant.now().plusSeconds(1), 10))
                    .isGreaterThanOrEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM github_sync_task
                    WHERE source_id = ? AND kind = 'REPOSITORY_SYNC' AND status = 'QUEUED'
                    """, Integer.class, sourceId)).isEqualTo(1);
        } finally {
            jdbc.update("""
                    UPDATE github_sync_admission_policy
                    SET global_high = ?, global_low = ?, namespace_high = ?, namespace_low = ?
                    WHERE id = TRUE
                    """, previousPolicy.get("global_high"), previousPolicy.get("global_low"),
                    previousPolicy.get("namespace_high"), previousPolicy.get("namespace_low"));
            jdbc.update("""
                    UPDATE github_sync_task SET status = 'FAILED'
                    WHERE source_id = ? AND kind = 'REPOSITORY_SYNC' AND status = 'QUEUED'
                    """, sourceId);
        }
    }

    @Test
    void versionedSearchRuleCreatesDeduplicatedLeadsWithoutSources() throws Exception {
        verifySearchRuleDiscovery(false);
    }

    @Test
    void incompleteSearchSplitsFixedWindowBeforeCompleting() throws Exception {
        verifySearchRuleDiscovery(true);
    }

    @Test
    void shortenedSearchPageResumesThroughJavaAndDeduplicatesRepositoryIds() throws Exception {
        var request = new GithubSearchRuleRequest("Interop changing page", List.of(
                new GithubSearchSelectorRequest("TOPIC", "agent-skills", "EXTENSION"),
                new GithubSearchSelectorRequest("KEYWORD", "agent skills", "CORE")), null);
        var audit = new AuditRequestContext("127.0.0.1", "interop-test");
        var entry = discoveryAdmin.createSearchRule(request, "github-sync-publisher", audit);
        discoveryAdmin.setEnabled(entry.id(), true, "github-sync-publisher", audit);
        assertThat(discoveryEntries.enqueueDue(Instant.now(), 10)).isEqualTo(1);
        UUID taskId = jdbc.queryForObject("""
                SELECT id FROM github_sync_task WHERE dedupe_key = ?
                """, UUID.class, "discovery:" + entry.id());
        jdbc.update("""
                UPDATE github_sync_task
                SET snapshot_json = jsonb_set(snapshot_json, '{candidateBudget}', '3'::jsonb)
                WHERE id = ?
                """, taskId);

        assertThat(runChangingSearchPageWorker(taskId, "initial")).contains("PASS");
        assertThat(jdbc.queryForObject("""
                SELECT status FROM github_sync_task WHERE id = ?
                """, String.class, taskId)).isEqualTo("RETRY_WAIT");
        String checkpoint = jdbc.queryForObject("""
                SELECT discovery_checkpoint FROM github_sync_task WHERE id = ?
                """, String.class, taskId);
        assertThat(objectMapper.readTree(checkpoint).path("offset").asInt()).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_discovery_task_hit WHERE task_id = ?
                """, Integer.class, taskId)).isEqualTo(3);

        jdbc.update("""
                UPDATE github_sync_task
                SET retry_at = CURRENT_TIMESTAMP - INTERVAL '1 second',
                    due_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE id = ?
                """, taskId);
        assertThat(runChangingSearchPageWorker(taskId, "shrunk")).contains("PASS");
        assertThat(jdbc.queryForObject("""
                SELECT status FROM github_sync_task WHERE id = ?
                """, String.class, taskId)).isEqualTo("ACQUISITION_COMPLETE");
        assertThat(jdbc.queryForObject("""
                SELECT lease_generation FROM github_sync_task WHERE id = ?
                """, Long.class, taskId)).isEqualTo(2);
        assertThat(jdbc.queryForList("""
                SELECT repository_id FROM github_discovery_task_hit
                WHERE task_id = ? ORDER BY repository_id
                """, Long.class, taskId)).containsExactly(9400001L, 9400002L, 9400003L, 9400006L);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_discovery_task_hit
                WHERE task_id = ? AND repository_id = 9400002
                """, Integer.class, taskId)).isEqualTo(1);
        assertThat(objectMapper.readTree(jdbc.queryForObject("""
                SELECT discovery_checkpoint FROM github_sync_task WHERE id = ?
                """, String.class, taskId)).path("revisit").asInt()).isEqualTo(1);
    }

    private String runChangingSearchPageWorker(UUID taskId, String phase) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("go", "test", "./internal/worker",
                "-run", "^TestInteropChangingSearchPage$", "-count=1", "-v");
        builder.directory(findGoModule().toFile()).redirectErrorStream(true);
        builder.environment().put("SKILLHUB_INTEROP_SEARCH_PAGE_PHASE", phase);
        builder.environment().put("SKILLHUB_INTEROP_JAVA_URL", baseUrl() + "/internal/github-sync/v1");
        builder.environment().put("SKILLHUB_INTEROP_SERVICE_TOKEN", SERVICE_TOKEN);
        builder.environment().put("SKILLHUB_INTEROP_TASK_ID", taskId.toString());
        Process process = builder.start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Go changing search page worker timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as("Go changing search page worker: %s", output).isZero();
        return output;
    }

    private void verifySearchRuleDiscovery(boolean saturated) throws Exception {
        var request = new GithubSearchRuleRequest(
                saturated ? "Interop saturated search" : "Interop search", List.of(
                new GithubSearchSelectorRequest("TOPIC", "agent-skills", "EXTENSION"),
                new GithubSearchSelectorRequest("KEYWORD", "agent skills", "CORE")), null);
        var audit = new AuditRequestContext("127.0.0.1", "interop-test");
        var entry = discoveryAdmin.createSearchRule(request, "github-sync-publisher", audit);
        var enabled = discoveryAdmin.setEnabled(entry.id(), true, "github-sync-publisher", audit);
        assertThat(enabled.ruleVersion()).isEqualTo(2);
        assertThat(discoveryEntries.enqueueDue(Instant.now(), 10)).isEqualTo(1);
        UUID taskId = jdbc.queryForObject("""
                SELECT id FROM github_sync_task WHERE dedupe_key = ?
                """, UUID.class, "discovery:" + entry.id());
        int sourcesBefore = jdbc.queryForObject("""
                SELECT count(*) FROM github_source_binding
                """, Integer.class);

        assertThat(runSearchRuleWorker(taskId, saturated)).contains("PASS");
        assertThat(jdbc.queryForObject("""
                SELECT status FROM github_sync_task WHERE id = ?
                """, String.class, taskId)).isEqualTo("ACQUISITION_COMPLETE");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_discovery_task_hit WHERE task_id = ?
                """, Integer.class, taskId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_discovery_batch WHERE task_id = ?
                """, Integer.class, taskId)).isEqualTo(saturated ? 5 : 3);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_discovery_candidate_origin
                WHERE origin_type = 'SEARCH_RULE' AND origin_key = ?
                """, Integer.class, entry.id().toString())).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_source_binding
                """, Integer.class)).isEqualTo(sourcesBefore);
    }

    private String runSearchRuleWorker(UUID taskId, boolean saturated) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("go", "test", "./internal/worker",
                "-run", "^TestInteropSearchRuleDiscovery$", "-count=1", "-v");
        builder.directory(findGoModule().toFile()).redirectErrorStream(true);
        builder.environment().put("SKILLHUB_INTEROP_SEARCH_RULE", "1");
        builder.environment().put("SKILLHUB_INTEROP_JAVA_URL", baseUrl() + "/internal/github-sync/v1");
        builder.environment().put("SKILLHUB_INTEROP_SERVICE_TOKEN", SERVICE_TOKEN);
        builder.environment().put("SKILLHUB_INTEROP_TASK_ID", taskId.toString());
        builder.environment().put("SKILLHUB_INTEROP_SEARCH_SATURATED", saturated ? "1" : "0");
        Process process = builder.start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Go search rule worker timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as("Go search rule worker: %s", output).isZero();
        return output;
    }

    @Test
    void curatedUserAndOrganizationDiscoverPublicLeadsWithoutPublishing() throws Exception {
        verifyCuratedOwnerDiscovery("fixture-user", "User", 987654324L);
        verifyCuratedOwnerDiscovery("fixture-org", "Organization", 987654325L);
    }

    private void verifyCuratedOwnerDiscovery(String owner, String ownerType,
                                             long repositoryId) throws Exception {
        var audit = new AuditRequestContext("127.0.0.1", "interop-test");
        var entry = discoveryAdmin.createCuratedOwner(new GithubCuratedOwnerRequest(owner, null),
                "github-sync-publisher", audit);
        assertThat(discoveryEntries.enqueueDue(Instant.now(), 10)).isEqualTo(1);
        UUID taskId = jdbc.queryForObject("""
                SELECT id FROM github_sync_task WHERE dedupe_key = ?
                """, UUID.class, "discovery:" + entry.id());
        int sourcesBefore = jdbc.queryForObject("""
                SELECT count(*) FROM github_source_binding
                """, Integer.class);

        assertThat(runCuratedOwnerWorker(taskId, owner, ownerType)).contains("PASS");
        assertThat(jdbc.queryForObject("""
                SELECT status FROM github_sync_task WHERE id = ?
                """, String.class, taskId)).isEqualTo("ACQUISITION_COMPLETE");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_discovery_task_hit
                WHERE task_id = ? AND repository_id = ?
                """, Integer.class, taskId, repositoryId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_discovery_candidate_origin origin
                JOIN github_discovery_candidate candidate ON candidate.id = origin.candidate_id
                WHERE origin.origin_type = 'CURATED_OWNER' AND origin.origin_key = ?
                  AND candidate.repository_id = ?
                """, Integer.class, owner, repositoryId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_source_binding
                """, Integer.class)).isEqualTo(sourcesBefore);
    }

    private String runCuratedOwnerWorker(UUID taskId, String owner, String ownerType) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("go", "test", "./internal/worker",
                "-run", "^TestInteropCuratedOwnerDiscovery$", "-count=1", "-v");
        builder.directory(findGoModule().toFile()).redirectErrorStream(true);
        builder.environment().put("SKILLHUB_INTEROP_OWNER_TYPE", ownerType);
        builder.environment().put("SKILLHUB_INTEROP_OWNER_LOGIN", owner);
        builder.environment().put("SKILLHUB_INTEROP_JAVA_URL", baseUrl() + "/internal/github-sync/v1");
        builder.environment().put("SKILLHUB_INTEROP_SERVICE_TOKEN", SERVICE_TOKEN);
        builder.environment().put("SKILLHUB_INTEROP_TASK_ID", taskId.toString());
        Process process = builder.start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Go curated owner worker timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as("Go curated owner worker: %s", output).isZero();
        return output;
    }

    private String runCuratedRepositoryWorker(UUID taskId) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("go", "test", "./internal/worker",
                "-run", "^TestInteropCuratedPublicRepository$", "-count=1", "-v");
        builder.directory(findGoModule().toFile()).redirectErrorStream(true);
        builder.environment().put("SKILLHUB_INTEROP_CURATED_REPOSITORY", "1");
        builder.environment().put("SKILLHUB_INTEROP_JAVA_URL", baseUrl() + "/internal/github-sync/v1");
        builder.environment().put("SKILLHUB_INTEROP_SERVICE_TOKEN", SERVICE_TOKEN);
        builder.environment().put("SKILLHUB_INTEROP_TASK_ID", taskId.toString());
        builder.environment().put("SKILLHUB_INTEROP_CURATED_MODE",
                "1".equals(System.getenv("SKILLHUB_GITHUB_SYNC_LIVE_CURATED")) ? "live" : "fixture");
        Process process = builder.start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Go curated repository worker timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as("Go curated repository worker: %s", output).isZero();
        return output;
    }

    @Test
    void credentialPoolFallsBackByQuotaResourceAcrossJavaAndGo() throws Exception {
        String depletedToken = "fixture-public-reader-search-exhausted";
        String healthyToken = "fixture-public-reader-search-healthy";
        UUID depletedId = createFixtureCredential("Search exhausted", depletedToken);
        UUID healthyId = createFixtureCredential("Search healthy", healthyToken);
        Instant now = Instant.now();
        systemCredentials.recordQuota(depletedId, "SEARCH", 0, now.plusSeconds(3600), now);
        try {
            var request = new GithubSearchRuleRequest("Public skills", List.of(
                    new GithubSearchSelectorRequest("TOPIC", "agent-skills", "EXTENSION"),
                    new GithubSearchSelectorRequest("KEYWORD", "agent skills", "CORE")), null);
            UUID healthyPreviewId = searchPreviews.queue(request, "interop-admin").id();
            assertThat(runSearchPreviewWorker(healthyPreviewId, "success", healthyId, healthyToken))
                    .contains("PASS");
            assertThat(searchPreviews.get(healthyPreviewId).status()).isEqualTo("ACQUISITION_COMPLETE");

            assertThat(systemCredentials.setEnabled(healthyId, false)).isTrue();
            UUID blockedPreviewId = searchPreviews.queue(request, "interop-admin").id();
            var unavailable = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                            URI.create(baseUrl() + "/internal/github-sync/v1/tasks/claim"))
                    .header("Authorization", "Bearer " + SERVICE_TOKEN)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""
                            {"protocolMajor":1,"kind":"DISCOVERY","workerId":"credential-wait-worker", "capabilities":["platform-pause/v1"]}
                            """)).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(unavailable.statusCode()).isEqualTo(204);
            assertThat(unavailable.body()).isEmpty();
            assertThat(searchPreviews.get(blockedPreviewId).status()).isEqualTo("RETRY_WAIT");
            assertThat(jdbc.queryForObject("SELECT official_pause_reason FROM github_sync_task WHERE id = ?",
                    String.class, blockedPreviewId)).isEqualTo("CREDENTIAL_UNAVAILABLE");
            jdbc.update("UPDATE github_sync_task SET status = 'FAILED' WHERE id = ?", blockedPreviewId);

            long namespaceId = jdbc.queryForObject("""
                    SELECT id FROM namespace WHERE slug = 'github' AND type = 'CATALOG'
                    """, Long.class);
            UUID runId = UUID.randomUUID();
            UUID curatedTaskId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO github_sync_run (id, namespace_id, kind)
                    VALUES (?, ?, 'DISCOVERY')
                    """, runId, namespaceId);
            jdbc.update("""
                    INSERT INTO github_sync_task
                        (id, run_id, namespace_id, kind, dedupe_key, priority, snapshot_json)
                    VALUES (?, ?, ?, 'DISCOVERY', ?, 100,
                        jsonb_build_object('entryType', 'CURATED_OWNER', 'owner', 'interop-owner',
                            'ruleVersion', 1, 'candidateBudget', 10))
                    """, curatedTaskId, runId, namespaceId, "credential-core:" + curatedTaskId);
            assertThat(runDiscoveryClient(curatedTaskId, "report", depletedId, depletedToken))
                    .contains("REPORTED");
            jdbc.update("""
                    UPDATE github_sync_task SET lease_expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                    WHERE id = ?
                    """, curatedTaskId);
            assertThat(runDiscoveryClient(curatedTaskId, "resume", depletedId, depletedToken))
                    .contains("COMPLETED");
            assertThat(jdbc.queryForObject("""
                    SELECT status FROM github_sync_task WHERE id = ?
                    """, String.class, curatedTaskId)).isEqualTo("ACQUISITION_COMPLETE");
            String snapshot = jdbc.queryForObject("""
                    SELECT snapshot_json::text FROM github_sync_task WHERE id = ?
                    """, String.class, curatedTaskId);
            assertThat(snapshot).doesNotContain(depletedToken, healthyToken);
        } finally {
            // Remove only this test's credentials; disabled configured rows intentionally prevent anonymous claims.
            jdbc.update("UPDATE github_sync_task SET credential_id = NULL WHERE credential_id IN (?, ?)", depletedId, healthyId);
            jdbc.update("DELETE FROM github_system_credential WHERE id IN (?, ?)", depletedId, healthyId);
        }
    }

    private UUID createFixtureCredential(String label, String token) {
        UUID id = UUID.randomUUID();
        var encrypted = credentialCipher.encrypt(id, token);
        Instant now = Instant.now();
        systemCredentials.create(new GithubSystemCredential(id, label,
                id.toString().replace("-", "").substring(0, 16), encrypted.keyVersion(),
                encrypted.nonce(), encrypted.ciphertext(), "ACTIVE", null, null, now, now));
        return id;
    }

    private String runSearchPreviewWorker(UUID taskId, String phase) throws Exception {
        return runSearchPreviewWorker(taskId, phase, null, null);
    }

    private String runSearchPreviewWorker(UUID taskId, String phase,
                                          UUID credentialId, String token) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("go", "test", "./internal/worker",
                "-run", "^TestInteropSearchPreview$", "-count=1", "-v");
        builder.directory(findGoModule().toFile()).redirectErrorStream(true);
        builder.environment().put("SKILLHUB_INTEROP_PREVIEW_PHASE", phase);
        builder.environment().put("SKILLHUB_INTEROP_JAVA_URL", baseUrl() + "/internal/github-sync/v1");
        builder.environment().put("SKILLHUB_INTEROP_SERVICE_TOKEN", SERVICE_TOKEN);
        builder.environment().put("SKILLHUB_INTEROP_TASK_ID", taskId.toString());
        if (credentialId != null) {
            builder.environment().put("SKILLHUB_INTEROP_EXPECT_CREDENTIAL_ID", credentialId.toString());
            builder.environment().put("SKILLHUB_INTEROP_EXPECT_CREDENTIAL_TOKEN", token);
        } else if (token != null) {
            builder.environment().put("SKILLHUB_INTEROP_EXPECT_ANONYMOUS", "1");
        }
        Process process = builder.start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Go search preview worker timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as("Go search preview worker: %s", output).isZero();
        return output;
    }

    private String runDiscoveryClient(UUID taskId, String phase) throws Exception {
        return runDiscoveryClient(taskId, phase, null, null);
    }

    private String runDiscoveryClient(UUID taskId, String phase,
                                      UUID credentialId, String token) throws Exception {
        return runDiscoveryClient(taskId, phase, credentialId, token, baseUrl());
    }

    private String runDiscoveryClient(UUID taskId, String phase,
                                      UUID credentialId, String token,
                                      String javaBaseUrl) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("go", "run",
                "./internal/controlplane/testdata/discoveryinterop");
        builder.directory(findGoModule().toFile()).redirectErrorStream(true);
        builder.environment().put("SKILLHUB_INTEROP_DISCOVERY_PHASE", phase);
        builder.environment().put("SKILLHUB_INTEROP_JAVA_URL",
                javaBaseUrl + "/internal/github-sync/v1");
        builder.environment().put("SKILLHUB_INTEROP_SERVICE_TOKEN", SERVICE_TOKEN);
        builder.environment().put("SKILLHUB_INTEROP_TASK_ID", taskId.toString());
        if (credentialId != null) {
            builder.environment().put("SKILLHUB_INTEROP_EXPECT_CREDENTIAL_ID", credentialId.toString());
            builder.environment().put("SKILLHUB_INTEROP_EXPECT_CREDENTIAL_TOKEN", token);
        }
        Process process = builder.start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Go discovery HTTP client timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as("Go discovery HTTP client: %s", output).isZero();
        return output;
    }

    private void assertSingleReceivedPackage(UUID taskId, String ingestionId) {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_package_admission WHERE task_id = ?
                """, Integer.class, taskId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_staged_upload upload
                JOIN github_sync_package_admission admission ON admission.id = upload.admission_id
                WHERE admission.task_id = ?
                """, Integer.class, taskId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_package_ingestion WHERE id = ? AND task_id = ?
                """, Integer.class, UUID.fromString(ingestionId), taskId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_package_ingestion WHERE task_id = ?
                """, Integer.class, taskId)).isEqualTo(1);
    }

    private void runRepositoryWorker(UUID taskId, String phase) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("go", "test", "./internal/worker",
                "-run", "^TestInteropProcessRepositoryRecovery$", "-count=1", "-v");
        builder.directory(findGoModule().toFile()).redirectErrorStream(true);
        builder.environment().put("SKILLHUB_INTEROP_WORKER_PHASE", phase);
        builder.environment().put("SKILLHUB_INTEROP_JAVA_URL", baseUrl() + "/internal/github-sync/v1");
        builder.environment().put("SKILLHUB_INTEROP_SERVICE_TOKEN", SERVICE_TOKEN);
        builder.environment().put("SKILLHUB_INTEROP_TASK_ID", taskId.toString());
        Process process = builder.start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Go repository worker timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as("Go repository worker: %s", output).isZero();
        assertThat(output).contains("PASS");
    }

    private String runGoClient(UUID taskId, UUID oversizedTaskId) throws Exception {
        return runGoClient(taskId, oversizedTaskId, COMMIT,
                Path.of(System.getenv("SKILLHUB_GITHUB_SYNC_ARCHIVE_FIXTURE")));
    }

    private String runGoClient(UUID taskId, int javaPort) throws Exception {
        return runGoClient(taskId, null, COMMIT,
                Path.of(System.getenv("SKILLHUB_GITHUB_SYNC_ARCHIVE_FIXTURE")),
                null, null, "http://127.0.0.1:" + javaPort);
    }

    private void claimInSeparateJvm(String ingestionId, Class<?> claimant) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"),
                claimant.getName(), ingestionId);
        builder.redirectErrorStream(true);
        builder.environment().put("SKILLHUB_RESTART_JDBC_URL", POSTGRES.getJdbcUrl());
        builder.environment().put("SKILLHUB_RESTART_JDBC_USER", POSTGRES.getUsername());
        builder.environment().put("SKILLHUB_RESTART_JDBC_PASSWORD", POSTGRES.getPassword());
        Process process = builder.start();
        if (!process.waitFor(45, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Separate Java claimant timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as("Separate Java claimant: %s", output).isZero();
        assertThat(output).contains("CLAIMED");
    }

    private String runGoClient(UUID taskId, UUID oversizedTaskId, String commit, Path archivePath)
            throws Exception {
        return runGoClient(taskId, oversizedTaskId, commit, archivePath, null, null);
    }

    private String runGoClient(UUID taskId, UUID oversizedTaskId, String commit, Path archivePath,
            String mode, String expectedIngestionId) throws Exception {
        return runGoClient(taskId, oversizedTaskId, commit, archivePath, mode,
                expectedIngestionId, baseUrl());
    }

    private String runGoClient(UUID taskId, UUID oversizedTaskId, String commit, Path archivePath,
            String mode, String expectedIngestionId, String javaBaseUrl) throws Exception {
        Process process = goClientBuilder(taskId, oversizedTaskId, commit, archivePath,
                mode, expectedIngestionId, javaBaseUrl).start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Go HTTP interoperability client timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertThat(process.exitValue()).as("Go HTTP interoperability client: %s", output).isZero();
        return output;
    }

    private ProcessBuilder goClientBuilder(UUID taskId, UUID oversizedTaskId, String commit,
            Path archivePath, String mode, String expectedIngestionId, String javaBaseUrl) {
        ProcessBuilder builder = new ProcessBuilder("go", "run",
                "./internal/controlplane/testdata/httpinterop");
        builder.directory(findGoModule().toFile()).redirectErrorStream(true);
        builder.environment().put("SKILLHUB_INTEROP_JAVA_URL",
                javaBaseUrl + "/internal/github-sync/v1");
        builder.environment().put("SKILLHUB_INTEROP_SERVICE_TOKEN", SERVICE_TOKEN);
        builder.environment().put("SKILLHUB_INTEROP_TASK_ID", taskId.toString());
        builder.environment().put("SKILLHUB_INTEROP_COMMIT_SHA", commit);
        builder.environment().put("SKILLHUB_INTEROP_SKILL_PATH", SKILL_PATH);
        builder.environment().put("SKILLHUB_INTEROP_ARCHIVE_PATH", archivePath.toAbsolutePath().toString());
        if (mode != null) {
            builder.environment().put("SKILLHUB_INTEROP_MODE", mode);
        }
        if (expectedIngestionId != null) {
            builder.environment().put("SKILLHUB_INTEROP_EXPECT_INGESTION_ID", expectedIngestionId);
        }
        if (S3_MODE) {
            builder.environment().put("SKILLHUB_INTEROP_UPLOAD_MODE", "S3_PRESIGNED_PUT");
            builder.environment().put("SKILLHUB_INTEROP_ALLOWED_S3_HOST",
                    URI.create(s3Endpoint).getAuthority());
            if (oversizedTaskId != null) {
                builder.environment().put("SKILLHUB_INTEROP_OVERSIZED_TASK_ID", oversizedTaskId.toString());
            }
        }
        return builder;
    }

    private UUID createRepositoryTask(long namespaceId, long sourceId, long repositoryId) {
        return createRepositoryTask(namespaceId, sourceId, repositoryId, COMMIT);
    }

    private UUID createRepositoryTask(long namespaceId, long sourceId, long repositoryId,
                                      String commit) {
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO github_sync_run (id, namespace_id, kind)
                VALUES (?, ?, 'REPOSITORY_SYNC')
                """, runId, namespaceId);
        jdbc.update("""
                INSERT INTO github_sync_task
                    (id, run_id, namespace_id, source_id, kind, mode, dedupe_key, priority,
                     snapshot_json, fixed_commit_sha)
                VALUES (?, ?, ?, ?, 'REPOSITORY_SYNC', 'FULL', ?, 100,
                    jsonb_build_object('repositoryId', ?, 'owner', 'openai', 'name', 'skills'), ?)
                """, taskId, runId, namespaceId, sourceId, "repository:" + repositoryId,
                repositoryId, commit);
        return taskId;
    }

    private Path revisionArchive(String marker) throws IOException {
        return revisionArchive(marker, null);
    }

    private Path archiveWithNestedSkill(String marker) throws IOException {
        Path revision = revisionArchive(marker);
        Path invalid = Files.createTempFile("skillhub-github-invalid-", ".zip");
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(revision));
             ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(invalid))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                output.putNextEntry(new ZipEntry(entry.getName()));
                input.transferTo(output);
                output.closeEntry();
                input.closeEntry();
            }
            output.putNextEntry(new ZipEntry("nested/SKILL.md"));
            output.write(("# " + marker + "\n").getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(invalid);
            throw exception;
        } finally {
            Files.deleteIfExists(revision);
        }
        return invalid;
    }

    private Path revisionArchive(String marker, String licenseMarker) throws IOException {
        Path variant = Files.createTempFile("skillhub-github-revision-", ".zip");
        boolean changed = false;
        boolean licenseChanged = false;
        // Go can emit valid STORED entries with data descriptors, which Java's streaming
        // reader rejects. Use the central directory, as the production archive validator does.
        try (ZipFile source = new ZipFile(
                Path.of(System.getenv("SKILLHUB_GITHUB_SYNC_ARCHIVE_FIXTURE")).toFile());
             OutputStream output = Files.newOutputStream(variant);
             ZipOutputStream zipOutput = new ZipOutputStream(output)) {
            var entries = source.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                zipOutput.putNextEntry(new ZipEntry(entry.getName()));
                byte[] contents;
                try (InputStream input = source.getInputStream(entry)) {
                    contents = input.readAllBytes();
                }
                zipOutput.write(contents);
                if (entry.getName().equals("SKILL.md") || entry.getName().endsWith("/SKILL.md")) {
                    zipOutput.write(("\n<!-- revision " + marker + " -->\n")
                            .getBytes(StandardCharsets.UTF_8));
                    changed = true;
                }
                if (licenseMarker != null && (entry.getName().equals("LICENSE.txt")
                        || entry.getName().endsWith("/LICENSE.txt"))) {
                    zipOutput.write(("\nLicense evidence revision " + licenseMarker + "\n")
                            .getBytes(StandardCharsets.UTF_8));
                    licenseChanged = true;
                }
                zipOutput.closeEntry();
            }
        }
        if (!changed || (licenseMarker != null && !licenseChanged)) {
            Files.deleteIfExists(variant);
            throw new IllegalStateException("Go archive has no SKILL.md for revision fixture");
        }
        return variant;
    }

    private Path findGoModule() {
        for (Path current = Path.of("").toAbsolutePath(); current != null; current = current.getParent()) {
            Path module = current.resolve("github-sync-go");
            if (Files.isRegularFile(module.resolve("go.mod"))) {
                return module;
            }
        }
        throw new IllegalStateException("Cannot locate github-sync-go module for HTTP interoperability test");
    }

    private void awaitVersionStatus(Long versionId, String expected) throws InterruptedException {
        // The opt-in external scanner does real analysis and may queue requests;
        // the local HTTP fixture should still finish within the short budget.
        long timeoutSeconds = scanner == null ? 180 : 15;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        String actual;
        do {
            actual = jdbc.queryForObject("SELECT status FROM skill_version WHERE id = ?",
                    String.class, versionId);
            if (expected.equals(actual)) {
                return;
            }
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        assertThat(actual).as("version %s status after %s seconds", versionId, timeoutSeconds)
                .isEqualTo(expected);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    private String skillSlug(long versionId) {
        return jdbc.queryForObject("""
                SELECT skill.slug FROM skill
                JOIN skill_version version ON version.skill_id = skill.id
                WHERE version.id = ?
                """, String.class, versionId);
    }

    private HttpResponse<String> anonymousSkillDetail(String namespace, String slug) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                baseUrl() + "/api/web/skills/" + namespace + "/" + slug)).GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
