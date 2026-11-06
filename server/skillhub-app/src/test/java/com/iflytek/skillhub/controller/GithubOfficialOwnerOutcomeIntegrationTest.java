package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.support.PostgresTestImage;
import static com.iflytek.skillhub.support.GithubSyncWorkerCapabilities.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.githubsync.*;
import com.iflytek.skillhub.infra.jpa.JdbcGithubOfficialOwnerRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
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
class GithubOfficialOwnerOutcomeIntegrationTest {
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
    @Autowired ObjectMapper mapper;
    @Autowired com.iflytek.skillhub.repository.GithubDeepDiscoveryQueryRepository readModels;
    @Autowired GithubSyncIngestionRepository ingestions;
    @Autowired GithubSyncLeaseService leases;
    @Autowired GithubOfficialDiscoveryRepository scheduler;
    @Autowired JdbcGithubOfficialOwnerRepository owners;
    @Autowired GithubOfficialLifecycleRepository lifecycle;
    @Autowired GithubDiscoveryOutcomeRepository outcomes;
    @Autowired GithubSyncPublicationRepository publications;
    @Autowired com.iflytek.skillhub.service.GithubSyncPublicationAppService publicationService;
    @Autowired com.iflytek.skillhub.service.GithubSyncArchiveValidator archiveValidator;
    @Autowired com.iflytek.skillhub.storage.ObjectStorageService storage;
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

    @Test void onlyValidatedIndependentSeedsExpandOnceAndLaterIndependentHitCanQualify() throws Exception {
        Fixture first = fixture(1101, 101, true);
        owners.reconcile(namespace, Instant.now());
        assertThat(ownerCount()).isZero(); // Uploaded/VALIDATING is not a valid package.
        assertThat(ingestions.validated(first.ingestion(), UUID.randomUUID(), DIGEST, 0, "{}", Instant.now())).isFalse();
        assertThat(ownerCount()).isZero();
        validate(first);
        owners.reconcile(namespace, Instant.now());
        assertThat(ownerCount()).isEqualTo(1);
        UUID ownerQuery = jdbc.queryForObject("SELECT id FROM github_deep_discovery_query WHERE query_source='OWNER'", UUID.class);
        Fixture expanded = fixture(1102, 202, false);
        origin(expanded.candidate(), ownerQuery);
        validate(expanded);
        Fixture nonOfficial = fixture(1103, 303, false);
        validate(nonOfficial);
        Fixture onlyBound = fixture(1104, 404, true);
        jdbc.update("UPDATE github_discovery_candidate SET status='BOUND' WHERE id=?", onlyBound.candidate());
        owners.reconcile(namespace, Instant.now());
        assertThat(ownerCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_source_binding WHERE repository_id=1103", Integer.class)).isEqualTo(1);
        origin(expanded.candidate(), catalogQuery);
        owners.reconcile(namespace, Instant.now());
        assertThat(ownerCount()).isEqualTo(2);
        Fixture sameOwner = fixture(1105, 101, true);
        validate(sameOwner);
        owners.reconcile(namespace, Instant.now());
        assertThat(ownerCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_discovery_outcome_event WHERE repository_id=1101 AND event_type='FIRST_VALID'", Integer.class)).isEqualTo(1);
        assertThat(ingestions.validated(first.ingestion(), first.validationToken(), DIGEST, 0, "{}", Instant.now())).isFalse();
    }

    @Test void transferredRepositoryCannotRetargetAnOwnerAndLegacyMetadataClearsUnknownIdentity() throws Exception {
        Fixture seed = fixture(1201, 111, true);
        validate(seed);
        owners.reconcile(namespace, Instant.now());
        assertThat(leases.updateRepositoryMetadata(seed.task(), 1, TOKEN, 1201, "new-owner", "skills", "main", 222L))
                .isEqualTo(GithubRepositoryMetadataResult.UPDATED);
        assertThat(jdbc.queryForObject("SELECT github_owner_id FROM github_source_binding WHERE repository_id=1201", Long.class)).isEqualTo(222L);
        owners.reconcile(namespace, Instant.now());
        assertThat(jdbc.queryForList("SELECT github_owner_id FROM github_deep_discovery_query WHERE query_source='OWNER'", Long.class)).containsExactly(111L);
        assertThat(leases.updateRepositoryMetadata(seed.task(), 1, TOKEN, 1201, "unknown-owner", "skills", "main"))
                .isEqualTo(GithubRepositoryMetadataResult.UPDATED);
        assertThat(jdbc.queryForObject("SELECT github_owner_id FROM github_source_binding WHERE repository_id=1201", Long.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT github_owner_id FROM github_discovery_candidate WHERE repository_id=1201", Long.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT jsonb_exists(snapshot_json, 'ownerId') FROM github_sync_task WHERE id=?", Boolean.class, seed.task())).isFalse();
        assertThat(jdbc.queryForList("SELECT github_owner_id FROM github_deep_discovery_query WHERE query_source='OWNER'", Long.class)).containsExactly(111L);
    }

    @Test void canonicalOwnerReportAppliesExclusionWithoutChangingFrozenIdentity() throws Exception {
        Fixture seed = fixture(1301, 131, true);
        validate(seed);
        assertThat(scheduler.enqueueDue(Instant.now(), 1)).isEqualTo(1);
        assertThat(leases.claim(GithubSyncTaskKind.DISCOVERY, "search-only",platform(GithubOfficialDiscoveryCatalog.REQUIRED_CAPABILITIES))).isEmpty();
        var lease = leases.claim(GithubSyncTaskKind.DISCOVERY, "owner-worker",platform(GithubOfficialDiscoveryCatalog.OWNER_REQUIRED_CAPABILITIES)).orElseThrow();
        var snapshot = mapper.readTree(lease.task().snapshotJson()).path("officialDiscovery");
        assertThat(snapshot.path("ownerId").asLong()).isEqualTo(131);
        assertThat(snapshot.path("mode").asText()).isEqualTo("OWNER_RECHECK");
        jdbc.update("UPDATE github_deep_discovery_config SET exclusions_json='[{\"kind\":\"OWNER\",\"value\":\"renamed-owner\"}]'::jsonb WHERE id");
        var cp = mapper.createObjectNode().put("formatVersion", 1).put("nextPage", 12345).put("coverage", "IN_PROGRESS");
        for (String field : List.of("queryId", "queryVersion", "strategyVersion", "shardId")) cp.set(field, snapshot.get(field));
        var batch = new GithubDiscoveryBatch(null, cp.toString(), List.of(), 0, null);
        var metadata = new GithubOfficialDiscoveryBatch(lease.task().taskId(), "IN_PROGRESS", 1, List.of(), null, "renamed-owner");
        lifecycle.report(lease.task().taskId(), lease.task().leaseGeneration(), lease.token(), batch, metadata);
        lifecycle.report(lease.task().taskId(), lease.task().leaseGeneration(), lease.token(), batch, metadata);
        assertThat(lifecycle.control(lease.task().taskId(), lease.task().leaseGeneration(), lease.token()).reason()).isEqualTo("EXCLUDED");
        assertThat(jdbc.queryForObject("SELECT snapshot_json->'officialDiscovery'->>'ownerId' FROM github_sync_task WHERE id=?", String.class, lease.task().taskId())).isEqualTo("131");
        assertThat(jdbc.queryForObject("SELECT owner_login FROM github_deep_discovery_query WHERE query_source='OWNER'", String.class)).isEqualTo("renamed-owner");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_official_discovery_shard", Integer.class)).isZero();
        lifecycle.pauseDiscovery(lease.task().taskId(), lease.task().leaseGeneration(), lease.token(), cp.toString());
        assertThat(leases.claim(GithubSyncTaskKind.DISCOVERY, "owner-worker",platform(GithubOfficialDiscoveryCatalog.OWNER_REQUIRED_CAPABILITIES))).isEmpty();
    }

    @Test void firstValidationAttributionIsNotRewrittenByLaterOfficialOrigins() throws Exception {
        Fixture fixture = fixture(1401, 141, false);
        validate(fixture);
        origin(fixture.candidate(), catalogQuery);
        outcomes.firstValid(namespace, 1401, Instant.now());
        assertThat(jdbc.queryForObject("SELECT official_at_event FROM github_discovery_outcome_event WHERE repository_id=1401 AND event_type='FIRST_VALID'", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_discovery_outcome_event WHERE repository_id=1401 AND event_type='FIRST_VALID'", Integer.class)).isEqualTo(1);
    }

    @Test void realSkillVersionEventsAreSeparateAndNoChangeDoesNotCreateAnOutcome() throws Exception {
        Fixture fixture = fixture(1501, 151, true);
        validate(fixture);
        var claim = publications.claim(UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(600)).orElseThrow();
        long skill = jdbc.queryForObject("INSERT INTO skill(namespace_id,slug,owner_id) VALUES (?,'owner-outcome','github-sync-publisher') RETURNING id", Long.class, namespace);
        long first = jdbc.queryForObject("INSERT INTO skill_version(skill_id,version,created_by) VALUES (?,'1.0.0','github-sync-publisher') RETURNING id", Long.class, skill);
        long second = jdbc.queryForObject("INSERT INTO skill_version(skill_id,version,created_by) VALUES (?,'1.0.1','github-sync-publisher') RETURNING id", Long.class, skill);
        publications.recordOutcome(claim, skill, first, true, Instant.now());
        publications.recordOutcome(claim, skill, first, true, Instant.now());
        publications.recordOutcome(claim, skill, second, false, Instant.now());
        publications.recordOutcome(claim, skill, second, false, Instant.now());
        assertThat(jdbc.queryForList("SELECT event_type FROM github_discovery_outcome_event WHERE repository_id=1501 AND event_type IN ('NEW_SKILL','UPDATED_VERSION') ORDER BY event_type", String.class))
                .containsExactly("NEW_SKILL", "UPDATED_VERSION");
        assertThat(jdbc.queryForObject("SELECT subject_key FROM github_discovery_outcome_event WHERE event_type='NEW_SKILL'", Long.class)).isEqualTo(skill);
        assertThat(jdbc.queryForObject("SELECT subject_key FROM github_discovery_outcome_event WHERE event_type='UPDATED_VERSION'", Long.class)).isEqualTo(second);
        int count = jdbc.queryForObject("SELECT count(*) FROM github_discovery_outcome_event", Integer.class);
        assertThat(publications.noChange(claim, Instant.now())).isTrue();
        assertThat(publications.noChange(claim, Instant.now())).isFalse();
        assertThat(jdbc.queryForObject("SELECT status FROM github_sync_package_ingestion WHERE id=?", String.class, fixture.ingestion())).isEqualTo("NO_CHANGE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_discovery_outcome_event", Integer.class)).isEqualTo(count);
    }

    @Test void actualPublicationCreatesOneSkillThenOneVersionAndIdenticalArchiveIsNoChange() throws Exception {
        Fixture first = publishArchive(1601, "First content.");
        long skill = jdbc.queryForObject("SELECT published_skill_id FROM github_sync_package_ingestion WHERE id=?", Long.class, first.ingestion());
        assertThat(jdbc.queryForObject("SELECT status FROM github_sync_package_ingestion WHERE id=?", String.class, first.ingestion())).isEqualTo("READY_FOR_REVIEW");
        Fixture updated = publishArchive(1601, "Updated content.");
        assertThat(jdbc.queryForObject("SELECT published_skill_id FROM github_sync_package_ingestion WHERE id=?", Long.class, updated.ingestion())).isEqualTo(skill);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM skill_version WHERE skill_id=?", Integer.class, skill)).isEqualTo(2);
        Fixture identical = publishArchive(1601, "Updated content.");
        assertThat(jdbc.queryForObject("SELECT status FROM github_sync_package_ingestion WHERE id=?", String.class, identical.ingestion())).isEqualTo("NO_CHANGE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM skill_version WHERE skill_id=?", Integer.class, skill)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT event_type FROM github_discovery_outcome_event WHERE repository_id=1601 AND event_type IN ('NEW_SKILL','UPDATED_VERSION') ORDER BY event_type", String.class))
                .containsExactly("NEW_SKILL", "UPDATED_VERSION");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM review_task WHERE skill_id=?", Integer.class, skill)).isEqualTo(2);
        long version=jdbc.queryForObject("SELECT published_version_id FROM github_sync_package_ingestion WHERE id=?",Long.class,updated.ingestion());
        long review=jdbc.queryForObject("SELECT id FROM review_task WHERE github_sync_ingestion_id=?",Long.class,updated.ingestion());
        var path=readModels.paths(namespace,1601,0,20,Instant.now()).items().getFirst();
        assertThat(path.skillId()).isEqualTo(skill);assertThat(path.versionId()).isEqualTo(version);assertThat(path.reviewId()).isEqualTo(review);
        assertThat(path.outcome()).isEqualTo("ACCEPTED");
        assertThat(path.runId()).isEqualTo(jdbc.queryForObject("SELECT run_id FROM github_sync_task WHERE id=?",UUID.class,identical.task()));

        String content=jdbc.queryForObject("SELECT content_sha256 FROM github_sync_package_ingestion WHERE id=?",String.class,identical.ingestion());
        jdbc.update("UPDATE github_sync_package_ingestion SET content_sha256=? WHERE id=?","c".repeat(64),identical.ingestion());
        assertNoPathPublication(1601);
        jdbc.update("UPDATE github_sync_package_ingestion SET content_sha256=?,status='BLOCKED' WHERE id=?",content,identical.ingestion());
        assertNoPathPublication(1601); // An unresolved new attempt must not inherit an older review.
        jdbc.update("UPDATE github_sync_package_ingestion SET status='NO_CHANGE' WHERE id=?",identical.ingestion());
        Fixture unrelated=fixture(1602,161,true);
        jdbc.update("UPDATE github_sync_package_ingestion SET content_sha256=?,published_skill_id=?,published_version_id=? WHERE id=?",content,skill,version,unrelated.ingestion());
        jdbc.update("UPDATE github_source_skill_binding SET latest_ingestion_id=? WHERE namespace_id=? AND repository_id=1601",unrelated.ingestion(),namespace);
        assertNoPathPublication(1601); // Even matching hashes and IDs cannot cross repository coordinates.
        jdbc.update("UPDATE github_source_skill_binding SET latest_ingestion_id=? WHERE namespace_id=? AND repository_id=1601",updated.ingestion(),namespace);
        long firstVersion=jdbc.queryForObject("SELECT published_version_id FROM github_sync_package_ingestion WHERE id=?",Long.class,first.ingestion());
        jdbc.update("UPDATE github_sync_package_ingestion SET published_version_id=? WHERE id=?",firstVersion,updated.ingestion());
        assertNoPathPublication(1601); // Version must belong to the resolved publication ingestion.
        jdbc.update("UPDATE github_sync_package_ingestion SET published_version_id=? WHERE id=?",version,updated.ingestion());
        assertThat(readModels.paths(namespace,1601,0,20,Instant.now()).items().getFirst().reviewId()).isEqualTo(review);
    }

    @Test void revisionsKeepExplicitRetryAndIncreaseForChangeAndContentReturn() throws Exception {
        Fixture first = publishArchive(1701, "A");
        assertRevision(first, 1);
        jdbc.update("UPDATE github_sync_package_ingestion SET status='REJECTED' WHERE id=?", first.ingestion());
        long source = jdbc.queryForObject("SELECT source_id FROM github_source_skill_binding WHERE repository_id=1701", Long.class);
        assertThat(publications.requestRejectedRetry(namespace, source, "skills/demo", Instant.now())).isTrue();
        Fixture retry = publishArchive(1701, "A");
        assertRevision(retry, 1);
        Fixture changed = publishArchive(1701, "B");
        assertRevision(changed, 2);
        Fixture returned = publishArchive(1701, "A");
        assertRevision(returned, 3);
        Fixture noChange = publishArchive(1701, "A");
        assertThat(jdbc.queryForObject("SELECT status FROM github_sync_package_ingestion WHERE id=?", String.class, noChange.ingestion())).isEqualTo("NO_CHANGE");
        assertThat(jdbc.queryForList("SELECT github_sync_revision FROM skill_version WHERE skill_id=(SELECT published_skill_id FROM github_sync_package_ingestion WHERE id=?) ORDER BY id", Integer.class, returned.ingestion()))
                .containsExactly(1, 1, 2, 3);
        assertThat(jdbc.queryForObject("SELECT latest_revision FROM github_source_skill_binding WHERE repository_id=1701", Integer.class)).isEqualTo(3);
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

    private void assertNoPathPublication(long repository) {
        var path=readModels.paths(namespace,repository,0,20,Instant.now()).items().getFirst();
        assertThat(path.skillId()).isNull();assertThat(path.versionId()).isNull();assertThat(path.reviewId()).isNull();
    }

    private Fixture publishArchive(long repository, String content) throws Exception {
        Fixture fixture = prepareArchive(repository, content);
        var claim = publications.claim(UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(600)).orElseThrow();
        assertThat(claim.id()).isEqualTo(fixture.ingestion());
        publicationService.process(claim);
        publicationService.process(claim);
        return fixture;
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

    private int ownerCount() { return jdbc.queryForObject("SELECT count(*) FROM github_deep_discovery_query WHERE query_source='OWNER'", Integer.class); }
    private void origin(long candidate, UUID query) {
        jdbc.update("INSERT INTO github_discovery_candidate_origin(candidate_id,origin_type,origin_key) VALUES (?,'OFFICIAL_DISCOVERY',?) ON CONFLICT DO NOTHING", candidate, query.toString());
    }
    private void validate(Fixture fixture) {
        assertThat(ingestions.validated(fixture.ingestion(), fixture.validationToken(), DIGEST, 0, "{}", Instant.now())).isTrue();
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
        // This class wraps several logical worker attempts in one test transaction.
        // Preserve their real-world creation order instead of PostgreSQL now()'s fixed transaction time.
        jdbc.update("UPDATE github_sync_package_ingestion SET created_at=clock_timestamp() WHERE id=?", ingestion);
        return new Fixture(task, ingestion, validation, candidate);
    }
    private record Fixture(UUID task, UUID ingestion, UUID validationToken, long candidate) {}
}
