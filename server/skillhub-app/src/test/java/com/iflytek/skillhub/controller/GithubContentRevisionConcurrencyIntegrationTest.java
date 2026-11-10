package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.support.PostgresTestImage;
import com.iflytek.skillhub.githubsync.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {"skillhub.builtin-skills.enabled=false",
        "skillhub.security.scanner.enabled=true", "skillhub.security.scanner.mode=upload"})
class GithubContentRevisionConcurrencyIntegrationTest {
    @org.junit.jupiter.api.io.TempDir static java.nio.file.Path storageDirectory;
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PostgresTestImage.name());
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry registry) {
        registry.add("skillhub.storage.local.base-path", () -> storageDirectory.toString());
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
    @org.springframework.boot.test.mock.mockito.MockBean
    org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    @org.springframework.boot.test.mock.mockito.MockBean
    org.redisson.api.RedissonClient redissonClient;
    @org.springframework.boot.test.mock.mockito.MockBean
    com.iflytek.skillhub.stream.ScanTaskConsumer scanTaskConsumer;
    @Autowired JdbcTemplate jdbc;
    @Autowired GithubSyncIngestionRepository ingestions;
    @Autowired GithubOfficialDiscoveryRepository scheduler;
    @Autowired GithubSyncPublicationRepository publications;
    @Autowired com.iflytek.skillhub.service.GithubSyncPublicationAppService publicationService;
    @Autowired com.iflytek.skillhub.service.GithubSyncArchiveValidator archiveValidator;
    @Autowired com.iflytek.skillhub.storage.ObjectStorageService storage;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired com.iflytek.skillhub.repository.GithubSyncReviewSourceQueryRepository reviewSources;
    @Autowired com.iflytek.skillhub.repository.GithubSkillSourceQueryRepository marketSources;
    long namespace;
    UUID catalogQuery;
    static final String TOKEN = "e".repeat(64), DIGEST = "b".repeat(64);

    @BeforeEach void setup() {
        namespace = jdbc.queryForObject("SELECT namespace_id FROM github_deep_discovery_config WHERE id", Long.class);
        scheduler.enqueueDue(Instant.now(), 1); // Disabled configuration reconciles the real Java catalog only.
        catalogQuery = jdbc.queryForObject("SELECT id FROM github_deep_discovery_query ORDER BY query_key LIMIT 1", UUID.class);
        jdbc.update("UPDATE github_deep_discovery_query SET next_due_at=now()+INTERVAL '2 days'");
        jdbc.update("UPDATE github_deep_discovery_config SET enabled=TRUE WHERE id");
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void rollbackAndIndependentConcurrentTransactionsDoNotConsumeOrDuplicateRevisions() throws Exception {
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        Fixture first = prepareArchive(1801, "A");
        var claim = publications.claim(UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(600)).orElseThrow();
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            try { publicationService.process(claim); } catch (java.io.IOException exception) { throw new RuntimeException(exception); }
            assertRevision(first, 1);
            throw new IllegalStateException("rollback after revision assignment");
        })).isInstanceOf(IllegalStateException.class).hasMessage("rollback after revision assignment");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_source_skill_binding WHERE repository_id=1801", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM skill_version WHERE github_sync_ingestion_id=?", Integer.class, first.ingestion())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM review_task WHERE github_sync_ingestion_id=?", Integer.class, first.ingestion())).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM github_sync_package_ingestion WHERE id=?", String.class, first.ingestion())).isEqualTo("VALIDATED");
        publicationService.process(claim);
        assertRevision(first, 1);
        Fixture second = prepareArchive(1801, "B");
        var secondClaim = publications.claim(UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(600)).orElseThrow();
        Fixture third = prepareArchive(1801, "A");
        var thirdClaim = publications.claim(UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(600)).orElseThrow();
        var locked = new java.util.concurrent.CountDownLatch(1);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var olderPid = new java.util.concurrent.atomic.AtomicInteger();
        var newerPid = new java.util.concurrent.atomic.AtomicInteger();
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var older = executor.submit(() -> transaction.executeWithoutResult(status -> {
                olderPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                assertThat(publications.lockAndVerify(secondClaim, Instant.now())).isTrue();
                locked.countDown();
                try {
                    assertThat(release.await(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    publicationService.process(secondClaim);
                } catch (Exception exception) { throw new RuntimeException(exception); }
            }));
            java.util.concurrent.Future<?> newer;
            try {
                assertThat(locked.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                newer = executor.submit(() -> transaction.executeWithoutResult(status -> {
                    newerPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                    entered.countDown();
                    try { publicationService.process(thirdClaim); }
                    catch (java.io.IOException exception) { throw new RuntimeException(exception); }
                }));
                assertThat(entered.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThat(newerPid.get()).isNotEqualTo(olderPid.get());
                // A third connection observes actual PostgreSQL lock contention; thread timing is not proof.
                try (var observer = java.sql.DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                     var statement = observer.prepareStatement("SELECT ? = ANY(pg_blocking_pids(?))")) {
                    statement.setInt(1, olderPid.get());
                    statement.setInt(2, newerPid.get());
                    statement.setQueryTimeout(2);
                    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
                    boolean blockedByOlder = false;
                    while (System.nanoTime() < deadline) {
                        try (var result = statement.executeQuery()) {
                            result.next();
                            blockedByOlder = result.getBoolean(1);
                        }
                        if (blockedByOlder) break;
                        Thread.sleep(10); // Poll pacing only; the database predicate determines success.
                    }
                    assertThat(blockedByOlder).as("newer database session is blocked by the older transaction").isTrue();
                }
            } finally { release.countDown(); }
            older.get(30, java.util.concurrent.TimeUnit.SECONDS);
            newer.get(30, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertRevision(second, 2);
        assertRevision(third, 3);
        assertThat(jdbc.queryForObject("SELECT latest_revision FROM github_source_skill_binding WHERE repository_id=1801", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM skill_version WHERE skill_id=(SELECT published_skill_id FROM github_sync_package_ingestion WHERE id=?)", Integer.class, third.ingestion())).isEqualTo(3);
    }

    private void assertRevision(Fixture fixture, int expected) {
        long version = jdbc.queryForObject("SELECT published_version_id FROM github_sync_package_ingestion WHERE id=?", Long.class, fixture.ingestion());
        assertThat(jdbc.queryForObject("SELECT github_sync_revision FROM skill_version WHERE id=?", Integer.class, version)).isEqualTo(expected);
        String technical = jdbc.queryForObject("SELECT version FROM skill_version WHERE id=?", String.class, version);
        assertThat(technical).startsWith("0.0.0-g");
        long review = jdbc.queryForObject("SELECT id FROM review_task WHERE github_sync_ingestion_id=?", Long.class, fixture.ingestion());
        assertThat(reviewSources.findByReviewId(review).orElseThrow().syncRevision()).isEqualTo(expected);
        String status = jdbc.queryForObject("SELECT status FROM skill_version WHERE id=?", String.class, version);
        // A database fixture exercises the published-only projection, not approval workflow acceptance.
        jdbc.update("UPDATE skill_version SET status='PUBLISHED' WHERE id=?", version);
        assertThat(marketSources.findPublishedVersion(version).orElseThrow().syncRevision()).isEqualTo(expected);
        jdbc.update("UPDATE skill_version SET status=? WHERE id=?", status, version);
        assertThat(jdbc.queryForObject("SELECT version FROM skill_version WHERE id=?", String.class, version)).isEqualTo(technical);
    }

    private Fixture prepareArchive(long repository, String content) throws Exception {
        Fixture fixture = fixture(repository, 161, true);
        jdbc.update("INSERT INTO github_sync_path_result(task_id,commit_sha,skill_path,outcome,ingestion_id,sha256) SELECT task_id,commit_sha,skill_path,'ACCEPTED',id,sha256 FROM github_sync_package_ingestion WHERE id=?",fixture.ingestion());
        var bytes = new java.io.ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("SKILL.md"));
            zip.write(("---\nname: actual-owner-outcome-" + repository + "\ndescription: Exercise durable GitHub publication outcomes.\nlicense: MIT\n---\n# Actual owner outcome\n\n" + content + "\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        byte[] archive = bytes.toByteArray();
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archive));
        String key = jdbc.queryForObject("SELECT object_key FROM github_sync_package_ingestion WHERE id=?", String.class, fixture.ingestion());
        storage.putObject(key, new java.io.ByteArrayInputStream(archive), archive.length, "application/zip");
        var validation = archiveValidator.validate(key, archive.length, digest, "skills/demo");
        jdbc.update("UPDATE github_sync_package_ingestion SET size_bytes=?,sha256=? WHERE id=?", archive.length, digest, fixture.ingestion());
        assertThat(ingestions.validated(fixture.ingestion(), fixture.validationToken(), validation.contentSha256(), 0, "{}", Instant.now())).isTrue();
        return fixture;
    }

    private void origin(long candidate, UUID query) {
        jdbc.update("INSERT INTO github_discovery_candidate_origin(candidate_id,origin_type,origin_key) VALUES (?,'OFFICIAL_DISCOVERY',?) ON CONFLICT DO NOTHING", candidate, query.toString());
    }
    private Fixture fixture(long repo, long owner, boolean official) throws Exception {
        UUID task = UUID.randomUUID(), run = UUID.randomUUID(), admission = UUID.randomUUID(), upload = UUID.randomUUID(), ingestion = UUID.randomUUID(), validation = UUID.randomUUID();
        String commit = task.toString().replace("-", "") + "00000000";
        long candidate = jdbc.queryForObject("INSERT INTO github_discovery_candidate(namespace_id,repository_id,owner_login,repository_name,default_branch,github_owner_id) VALUES (?,?,'seed-owner','skills','main',?) ON CONFLICT (namespace_id,repository_id) DO UPDATE SET last_seen_at=now() RETURNING id", Long.class, namespace, repo, owner);
        if (official) origin(candidate, catalogQuery);
        jdbc.update("INSERT INTO github_sync_run(id,namespace_id,kind) VALUES (?,?,'REPOSITORY_SYNC')", run, namespace);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(TOKEN.getBytes(StandardCharsets.UTF_8)));
        jdbc.update("""
                INSERT INTO github_sync_task(id,run_id,namespace_id,kind,mode,status,dedupe_key,snapshot_json,lease_generation,lease_token_hash,lease_expires_at,leased_by)
                VALUES (?,?,?,'REPOSITORY_SYNC','FULL','LEASED',?,jsonb_build_object('repositoryId',?::bigint,'owner','seed-owner','name','skills','ownerId',?::bigint),1,?,now()+INTERVAL '1 hour','owner-test')
                """, task, run, namespace, task.toString(), repo, owner, hash);
        jdbc.update("UPDATE github_sync_task SET created_at=clock_timestamp() WHERE id=?", task);
        jdbc.update("UPDATE github_discovery_candidate SET status='VALIDATING',validation_task_id=? WHERE id=?", task, candidate);
        jdbc.update("INSERT INTO github_sync_package_admission(id,task_id,namespace_id,repository_id,commit_sha,skill_path,lease_generation) VALUES (?,?,?,?,?,'skills/demo',1)", admission, task, namespace, repo, commit);
        jdbc.update("INSERT INTO github_sync_staged_upload(id,admission_id,object_key,mode,ticket_hash,size_bytes,sha256,status,expires_at) VALUES (?,?,?,'LOCAL_STREAM',?,1,?,'ACCEPTED',now()+INTERVAL '1 hour')", upload, admission, "owner-test/"+upload, DIGEST, DIGEST);
        jdbc.update("""
                INSERT INTO github_sync_package_ingestion(id,task_id,admission_id,upload_id,namespace_id,repository_id,commit_sha,skill_path,object_key,size_bytes,sha256,status,validation_token,validation_expires_at)
                VALUES (?,?,?,?,?,?,?,'skills/demo',?,1,?,'VALIDATING',?,?)
                """, ingestion, task, admission, upload, namespace, repo, commit, "owner-test/"+upload, DIGEST, validation, Timestamp.from(Instant.now().plusSeconds(3600)));
        return new Fixture(task, ingestion, validation, candidate);
    }
    private record Fixture(UUID task, UUID ingestion, UUID validationToken, long candidate) {}
}
