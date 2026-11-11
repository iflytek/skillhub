package com.iflytek.skillhub.metrics;

import com.iflytek.skillhub.support.PostgresTestImage;
import com.iflytek.skillhub.config.SkillPublishProperties;
import com.iflytek.skillhub.storage.ObjectStorageService;
import com.iflytek.skillhub.storage.StorageProperties;
import com.iflytek.skillhub.storage.StoragePrefixUsage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class GithubSyncStagingCapacityMetricsTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(PostgresTestImage.name());

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired private JdbcTemplate jdbc;
    @TempDir Path storageRoot;

    @Test
    void tracksUncleanedReservationsAndWarnsWhenLocalDiskCannotFitOnePackage() {
        long namespaceId = jdbc.queryForObject("SELECT id FROM namespace WHERE slug = 'github'",
                Long.class);
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        String commit = "a".repeat(40);
        String digest = "b".repeat(64);
        Instant now = Instant.now();
        jdbc.update("INSERT INTO github_sync_run (id, namespace_id, kind) VALUES (?, ?, 'REPOSITORY_SYNC')",
                runId, namespaceId);
        jdbc.update("""
                INSERT INTO github_sync_task
                    (id, run_id, namespace_id, kind, mode, dedupe_key, snapshot_json)
                VALUES (?, ?, ?, 'REPOSITORY_SYNC', 'FULL', ?, '{}'::jsonb)
                """, taskId, runId, namespaceId, "staging-metric:" + taskId);
        for (int index = 0; index < 3; index++) {
            UUID admissionId = UUID.randomUUID();
            UUID uploadId = UUID.randomUUID();
            String path = "skills/demo-" + index;
            String key = "github-sync/staging/" + uploadId + ".zip";
            int size = (index + 2) * 10;
            jdbc.update("""
                    INSERT INTO github_sync_package_admission
                        (id, task_id, namespace_id, repository_id, commit_sha,
                         skill_path, lease_generation, status)
                    VALUES (?, ?, ?, 950001, ?, ?, 1, ?)
                    """, admissionId, taskId, namespaceId, commit, path,
                    index == 0 ? "ACTIVE" : "ACCEPTED");
            jdbc.update("""
                    INSERT INTO github_sync_staged_upload
                        (id, admission_id, object_key, mode, ticket_hash,
                         size_bytes, sha256, status, expires_at)
                    VALUES (?, ?, ?, 'LOCAL_STREAM', ?, ?, ?, ?, ?)
                    """, uploadId, admissionId, key, digest, size, digest,
                    index == 0 ? "ISSUED" : "ACCEPTED", Timestamp.from(now.plusSeconds(300)));
            if (index > 0) {
                jdbc.update("""
                        INSERT INTO github_sync_package_ingestion
                            (id, task_id, admission_id, upload_id, namespace_id,
                             repository_id, commit_sha, skill_path, object_key,
                             size_bytes, sha256, status, object_cleaned_at)
                        VALUES (?, ?, ?, ?, ?, 950001, ?, ?, ?, ?, ?, 'READY_FOR_REVIEW', ?)
                        """, UUID.randomUUID(), taskId, admissionId, uploadId, namespaceId,
                        commit, path, key, size, digest,
                        index == 2 ? Timestamp.from(now) : null);
            }
        }
        var properties = new StorageProperties();
        properties.getLocal().setBasePath(storageRoot.toString());
        var policy = new SkillPublishProperties();
        policy.setMaxPackageSize(Long.MAX_VALUE);
        var registry = new SimpleMeterRegistry();
        var objectStorage = mock(ObjectStorageService.class);
        var metrics = new GithubSyncStagingCapacityMetrics(jdbc, properties, objectStorage,
                policy, registry, 100);
        metrics.sample();

        assertThat(registry.get("skillhub.github.sync.staging.reserved.bytes")
                .gauge().value()).isEqualTo(50);
        assertThat(registry.get("skillhub.github.sync.staging.local.available.bytes")
                .gauge().value()).isPositive();
        assertThat(registry.get("skillhub.github.sync.staging.local.capacity.warning")
                .gauge().value()).isEqualTo(1);

        properties.setProvider("s3");
        metrics.sample();
        assertThat(registry.get("skillhub.github.sync.staging.local.available.bytes")
                .gauge().value()).isEqualTo(-1);
        assertThat(registry.get("skillhub.github.sync.staging.local.capacity.warning")
                .gauge().value()).isZero();
        when(objectStorage.listPrefixUsage("github-sync/staging/", 10_000))
                .thenReturn(Optional.of(new StoragePrefixUsage(80, 2, true)))
                .thenReturn(Optional.of(new StoragePrefixUsage(120, 2, true)))
                .thenReturn(Optional.of(new StoragePrefixUsage(120, 10_000, false)))
                .thenReturn(Optional.empty());
        metrics.sampleS3();
        assertThat(registry.get("skillhub.github.sync.staging.s3.used.bytes")
                .gauge().value()).isEqualTo(80);
        assertThat(registry.get("skillhub.github.sync.staging.s3.capacity.warning")
                .gauge().value()).isZero();
        metrics.sampleS3();
        assertThat(registry.get("skillhub.github.sync.staging.s3.used.bytes")
                .gauge().value()).isEqualTo(120);
        assertThat(registry.get("skillhub.github.sync.staging.s3.capacity.warning")
                .gauge().value()).isEqualTo(1);
        metrics.sampleS3();
        assertThat(registry.get("skillhub.github.sync.staging.s3.used.bytes")
                .gauge().value()).isEqualTo(-1);
        assertThat(registry.get("skillhub.github.sync.staging.s3.scan.complete")
                .gauge().value()).isZero();
        metrics.sampleS3();
        assertThat(registry.get("skillhub.github.sync.staging.s3.objects")
                .gauge().value()).isEqualTo(-1);
        assertThat(registry.get("skillhub.github.sync.staging.s3.capacity.warning")
                .gauge().value()).isEqualTo(1);
        registry.close();

        var unconfiguredRegistry = new SimpleMeterRegistry();
        var unconfigured = new GithubSyncStagingCapacityMetrics(jdbc, properties, objectStorage,
                policy, unconfiguredRegistry, 0);
        when(objectStorage.listPrefixUsage("github-sync/staging/", 10_000))
                .thenReturn(Optional.of(new StoragePrefixUsage(0, 0, true)))
                .thenReturn(Optional.of(new StoragePrefixUsage(1, 1, true)));
        unconfigured.sampleS3();
        assertThat(unconfiguredRegistry.get("skillhub.github.sync.staging.s3.capacity.warning")
                .gauge().value()).isZero();
        unconfigured.sampleS3();
        assertThat(unconfiguredRegistry.get("skillhub.github.sync.staging.s3.capacity.warning")
                .gauge().value()).isEqualTo(1);
        unconfiguredRegistry.close();
    }
}
