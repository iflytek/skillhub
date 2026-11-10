package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.support.PostgresTestImage;
import static com.iflytek.skillhub.support.GithubSyncWorkerCapabilities.platform;

import com.iflytek.skillhub.githubsync.*;
import com.iflytek.skillhub.service.GithubCredentialLeaseAppService;
import com.iflytek.skillhub.service.GithubDeepDiscoveryReadAppService;
import com.iflytek.skillhub.repository.GithubSyncWorkerPresenceRepository;
import com.iflytek.skillhub.repository.GithubDeepDiscoveryQueryRepository;
import com.iflytek.skillhub.dto.GithubDeepDiscoveryReadResponse.*;
import com.iflytek.skillhub.infra.jpa.JdbcGithubOfficialQuotaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "skillhub.builtin-skills.enabled=false")
class GithubOfficialPresenceQuotaIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PostgresTestImage.name());
    private static final String TEST_KEY = java.util.Base64.getEncoder().encodeToString(new java.security.SecureRandom().generateSeed(32));
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry registry) {
        registry.add("skillhub.github-sync.credential-keys", () -> "test=" + TEST_KEY);
        registry.add("skillhub.github-sync.active-credential-key-version", () -> "test");
        registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",POSTGRES::getUsername);
        registry.add("spring.datasource.password",POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        registry.add("spring.jpa.database-platform",()->"org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled",()->true);
        registry.add("spring.jpa.hibernate.ddl-auto",()->"validate");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired GithubOfficialDiscoveryRepository scheduler;
    @Autowired GithubCredentialLeaseAppService claims;
    @Autowired GithubCredentialCipher cipher;
    @Autowired GithubSystemCredentialRepository credentials;
    @Autowired GithubSyncWorkerPresenceRepository presence;
    @Autowired GithubDeepDiscoveryReadAppService reads;
    @Autowired GithubDeepDiscoveryQueryRepository queries;
    @Autowired GithubOfficialQuotaRepository quotas;

    @BeforeEach void setup() {
        jdbc.update("UPDATE github_deep_discovery_config SET enabled=TRUE WHERE id");
        jdbc.update("UPDATE github_sync_admission_policy SET global_high=10000,global_low=5000,namespace_high=10000,namespace_low=5000 WHERE id");
    }

    @Test void onlineWorkerWithoutPlatformCheckpointsIsNotCompatible() {
        presence.heartbeat("old-official", 1, GithubOfficialDiscoveryCatalog.OWNER_REQUIRED_CAPABILITIES);
        assertThat(presence.status().online()).isTrue();
        assertThat(presence.officialStatus().state()).isEqualTo("INCOMPATIBLE");
        assertThat(queries.compatibleWorker(Channel.REPOSITORY_METADATA)).isFalse();
        assertThat(queries.compatibleWorker(Channel.OWNER_EXPANSION)).isFalse();
        assertThat(queries.pauseCapableWorker()).isFalse();
        presence.heartbeat("old-official", 1, platform(GithubOfficialDiscoveryCatalog.OWNER_REQUIRED_CAPABILITIES));
        assertThat(presence.officialStatus().state()).isEqualTo("AVAILABLE");
        assertThat(queries.compatibleWorker(Channel.REPOSITORY_METADATA)).isTrue();
        assertThat(queries.compatibleWorker(Channel.OWNER_EXPANSION)).isTrue();
        assertThat(queries.pauseCapableWorker()).isTrue();
    }

    @Test void presenceIsChannelSpecificAndReconnectAndCleanupPreserveOnlineWorkers() {
        presence.heartbeat("search-only",1,platform(GithubOfficialDiscoveryCatalog.REQUIRED_CAPABILITIES));
        presence.heartbeat("owner-capable",1,platform(GithubOfficialDiscoveryCatalog.OWNER_REQUIRED_CAPABILITIES));
        assertThat(queries.compatibleWorker(Channel.REPOSITORY_METADATA)).isTrue();
        assertThat(queries.compatibleWorker(Channel.OWNER_EXPANSION)).isTrue();
        jdbc.update("UPDATE github_sync_worker_instance SET last_seen_at=CURRENT_TIMESTAMP-INTERVAL '2 minutes' WHERE worker_id='owner-capable'");
        assertThat(channel(Channel.REPOSITORY_METADATA).waits()).noneMatch(w -> w.reason().equals("CAPABILITY_UNAVAILABLE"));
        assertThat(channel(Channel.OWNER_EXPANSION).waits()).anyMatch(w -> w.reason().equals("CAPABILITY_UNAVAILABLE"));
        jdbc.update("UPDATE github_sync_worker_instance SET last_seen_at=CURRENT_TIMESTAMP-INTERVAL '2 minutes'");
        jdbc.update("UPDATE github_sync_worker_presence SET last_seen_at=CURRENT_TIMESTAMP-INTERVAL '2 minutes'");
        assertThat(reads.progress().channels()).allSatisfy(c -> {
            assertThat(c.state()).isEqualTo("WAITING");
            assertThat(c.waits()).anySatisfy(w -> {
                assertThat(w.reason()).isEqualTo("WORKER_UNAVAILABLE");
                assertThat(w.retryAt()).isNull();
                assertThat(w.recoveryCondition()).contains("不表示任务已执行");
            });
        });
        presence.heartbeat("owner-capable",1,platform(GithubOfficialDiscoveryCatalog.OWNER_REQUIRED_CAPABILITIES));
        assertThat(reads.progress().channels()).allSatisfy(c -> assertThat(c.waits())
                .noneMatch(w -> List.of("WORKER_UNAVAILABLE","CAPABILITY_UNAVAILABLE").contains(w.reason())));
        jdbc.update("UPDATE github_sync_worker_instance SET last_seen_at=CURRENT_TIMESTAMP-INTERVAL '100 days' WHERE worker_id='search-only'");
        assertThat(presence.deleteExpiredInstances(Instant.now().minusSeconds(90L*86400),500)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT worker_id FROM github_sync_worker_instance",String.class)).containsExactly("owner-capable");
        assertThat(new GithubSyncWorkerPresenceRepository(jdbc).officialStatus().compatibleInstanceCount()).isEqualTo(1);
    }

    @Test void twoEncryptedTokensRotateAfterResourceExhaustionAndNeverFallBackToAnonymous() {
        UUID first=credential("first"),second=credential("second");
        var leased=claim();
        assertThat(leased.credential().credentialId()).isIn(first,second);
        UUID used=leased.credential().credentialId();
        UUID other=used.equals(first)?second:first;
        observe(leased,"PRIMARY_EXHAUSTED",0,null);
        assertThat(quotas.cooldown("CORE",Instant.now())).isNull();
        assertThat(quotas.cooldownForLease(leased.lease().task().taskId(),Instant.now())).isAfter(Instant.now());
        assertThat(credentials.availableForClaim(Instant.now(),"CORE")).isPresent();
        assertThat(credentials.availableForClaim(Instant.now(),"SEARCH")).get().extracting(GithubSystemCredential::id).isEqualTo(other);
        releaseForNextClaim();
        var next=claim();
        assertThat(next.credential()).isNotNull();
        assertThat(next.credential().credentialId()).isEqualTo(other);
        observe(next,"PRIMARY_EXHAUSTED",0,null);
        releaseForNextClaim();
        assertThat(claimOptional()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_sync_task WHERE status='LEASED' AND credential_id IS NULL",Long.class)).isZero();
        assertThat(queries.credentialPoolUnavailable("SEARCH")).isTrue();
        assertThat(queries.credentialPoolUnavailable("CORE")).isFalse();
    }

    @Test void secondaryCooldownSurvivesRepositoryRecreationAndBlocksTheOtherToken() {
        credential("first");credential("second");
        var leased=claim();
        observe(leased,"SECONDARY_COOLDOWN",null,120);
        var recreated=new JdbcGithubOfficialQuotaRepository(jdbc);
        assertThat(recreated.cooldown("SEARCH",Instant.now())).isAfter(Instant.now());
        assertThat(recreated.cooldown("CORE",Instant.now())).isAfter(Instant.now());
        assertThat(credentials.availableForClaim(Instant.now(),"SEARCH")).isPresent();
        releaseForNextClaim();
        assertThat(claimOptional()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_sync_task WHERE official_pause_reason='QUOTA_COOLDOWN'",Long.class)).isPositive();
        jdbc.update("UPDATE github_official_secondary_cooldown SET cooldown_until=CURRENT_TIMESTAMP-INTERVAL '1 second'");
        releaseForNextClaim();
        assertThat(claimOptional()).isPresent();
    }

    @Test void expiredQuotaObservationsAreUnknownAndNotAnAggregatedRealTimeBalance() {
        credential("first");credential("second");
        var one=claim();observe(one,"OBSERVED",12,null);
        releaseForNextClaim();
        var two=claim();observe(two,"OBSERVED",21,null);
        assertThat(two.credential().credentialId()).isNotEqualTo(one.credential().credentialId());
        assertThat(reads.progress().quotas()).hasSize(2).extracting(Quota::remaining).containsExactlyInAnyOrder(12L,21L);
        jdbc.update("UPDATE github_official_quota_observation SET observed_at=CURRENT_TIMESTAMP-INTERVAL '10 minutes',valid_until=CURRENT_TIMESTAMP-INTERVAL '5 minutes'");
        assertThat(reads.progress().quotas()).hasSize(2).allSatisfy(q -> {
            assertThat(q.state()).isEqualTo("UNKNOWN");
            assertThat(q.remaining()).isNull();
            assertThat(q.subjectKey()).isNull();
            assertThat(q.retryAt()).isNull();
        });
    }

    @Test void anonymousPrimaryCooldownDoesNotBlockConfiguredCredentials() {
        var anonymous=claim();assertThat(anonymous.credential()).isNull();
        observe(anonymous,"PRIMARY_EXHAUSTED",0,null);
        assertThat(quotas.cooldown("SEARCH",Instant.now())).isAfter(Instant.now());
        credential("new-authenticated");
        assertThat(quotas.cooldown("SEARCH",Instant.now())).isNull();
        assertThat(quotas.cooldownForLease(anonymous.lease().task().taskId(),Instant.now())).isAfter(Instant.now());
        releaseForNextClaim();
        var authenticated=claim();assertThat(authenticated.credential()).isNotNull();
        assertThat(quotas.cooldownForLease(authenticated.lease().task().taskId(),Instant.now())).isNull();
    }

    @Test void credentialReplacementRejectsOldLeaseObservationsAndAuthInvalidDisablesCurrentCredential() {
        UUID id=credential("replaceable");var before=claim();
        var replacement=cipher.encrypt(id,"synthetic-replacement-"+UUID.randomUUID());
        credentials.replace(id,"replacement","f".repeat(16),replacement.keyVersion(),replacement.nonce(),replacement.ciphertext());
        Instant now=Instant.now();
        assertThat(claims.reportOfficialQuota(before.lease().task().taskId(),before.lease().task().leaseGeneration(),before.lease().token(),
                new GithubOfficialQuotaRepository.Observation("SEARCH",now,"AUTH_INVALID",null,null,null))).isFalse();
        assertThat(credentials.find(id).orElseThrow().status()).isEqualTo("ACTIVE");
        releaseForNextClaim();var current=claim();observe(current,"AUTH_INVALID",null,null);
        assertThat(credentials.find(id).orElseThrow().status()).isEqualTo("INVALID");
        releaseForNextClaim();assertThat(claimOptional()).isEmpty();
    }

    @Test void replacingExhaustedCredentialClearsOnlyItsOldObservationsAndQuota() {
        UUID id=credential("exhausted");var old=claim();observe(old,"PRIMARY_EXHAUSTED",0,null);
        assertThat(queries.credentialPoolUnavailable("SEARCH")).isTrue();
        var same=credentials.find(id).orElseThrow();
        credentials.replace(id,"renamed",same.fingerprint(),same.keyVersion(),same.nonce(),same.ciphertext());
        assertThat(queries.credentialPoolUnavailable("SEARCH")).isTrue();
        replaceCredential(id);
        assertThat(credentials.quotas(id)).isEmpty();assertThat(reads.progress().quotas()).isEmpty();
        assertThat(queries.credentialPoolUnavailable("SEARCH")).isFalse();
        releaseForNextClaim();assertThat(claim().credential().credentialId()).isEqualTo(id);
    }

    @Test
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void replacementWhileReportWaitsOnCredentialCannotPoisonNewToken() throws Exception {
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        try (var executor=java.util.concurrent.Executors.newSingleThreadExecutor()) {
            for (String mode:List.of("AUTH_INVALID","PRIMARY_EXHAUSTED","LEGACY_QUOTA","LEGACY_TASK_QUOTA","LEGACY_INVALID","LEGACY_LIMITED")) {
                UUID id=tx.execute(status -> credential("concurrent-"+mode));
                var old=tx.execute(status -> claim());
                assertThat(old.credential().credentialId()).isEqualTo(id);
                var started=new java.util.concurrent.CountDownLatch(1);
                var reporterPid=new java.util.concurrent.atomic.AtomicInteger();
                var report=new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<Boolean>>();
                tx.executeWithoutResult(status -> {
                    jdbc.queryForObject("SELECT id FROM github_system_credential WHERE id=? FOR UPDATE",UUID.class,id);
                    report.set(executor.submit(() -> tx.execute(reportTx -> {
                        reporterPid.set(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));started.countDown();
                        var lease=old.lease();Instant now=Instant.now();
                        String hash=org.apache.commons.codec.digest.DigestUtils.sha256Hex(lease.token());
                        return switch(mode) {
                            case "LEGACY_QUOTA" -> credentials.recordQuotaForLease(lease.task().taskId(),lease.task().leaseGeneration(),hash,id,"SEARCH",0,now.plusSeconds(300),now);
                            case "LEGACY_TASK_QUOTA" -> credentials.recordQuotaForTaskLease(lease.task().taskId(),lease.task().leaseGeneration(),hash,"SEARCH",0,now.plusSeconds(300),now);
                            case "LEGACY_INVALID" -> credentials.markInvalid(lease.task().taskId(),lease.task().leaseGeneration(),hash,id,now);
                            case "LEGACY_LIMITED" -> credentials.markRateLimited(lease.task().taskId(),lease.task().leaseGeneration(),hash,id,now.plusSeconds(300),now);
                            default -> claims.reportOfficialQuota(lease.task().taskId(),lease.task().leaseGeneration(),lease.token(),
                                    new GithubOfficialQuotaRepository.Observation("SEARCH",now,mode,mode.equals("PRIMARY_EXHAUSTED")?0:null,
                                            mode.equals("PRIMARY_EXHAUSTED")?now.plusSeconds(300):null,null));
                        };
                    })));
                    try {
                        assertThat(started.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                        long deadline=System.nanoTime()+java.time.Duration.ofSeconds(10).toNanos();boolean blocked=false;
                        while (System.nanoTime()<deadline) {
                            // Actual PostgreSQL lock wait is the barrier, not a timing assumption.
                            jdbc.execute("SELECT pg_stat_clear_snapshot()");
                            blocked=Boolean.TRUE.equals(jdbc.queryForObject("SELECT wait_event_type='Lock' FROM pg_stat_activity WHERE pid=?",Boolean.class,reporterPid.get()));
                            if(blocked) break;
                            Thread.sleep(10);
                        }
                        assertThat(blocked).as("report waits on locked credential: %s",mode).isTrue();
                        replaceCredential(id);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();throw new IllegalStateException(exception);
                    }
                });
                assertThat(report.get().get(10,java.util.concurrent.TimeUnit.SECONDS)).as(mode).isFalse();
                assertThat(credentials.find(id).orElseThrow().status()).isEqualTo("ACTIVE");
                assertThat(credentials.find(id).orElseThrow().rateLimitedUntil()).isNull();
                assertThat(credentials.quotas(id)).isEmpty();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM github_official_quota_observation WHERE credential_id=?",Integer.class,id)).isZero();
                tx.executeWithoutResult(status -> {
                    releaseForNextClaim();jdbc.update("UPDATE github_system_credential SET status='DISABLED' WHERE id=?",id);
                });
            }
        } finally {
            // Only this class's Testcontainers database; preserve no fixtures across methods.
            tx.executeWithoutResult(status -> {
                jdbc.execute("TRUNCATE github_deep_discovery_query,github_sync_run,github_system_credential CASCADE");
                jdbc.update("UPDATE github_official_discovery_budget SET next_lease_eligible_at=clock_timestamp()");
                jdbc.update("UPDATE github_deep_discovery_config SET enabled=FALSE WHERE id");
            });
        }
    }

    private void replaceCredential(UUID id) {
        var encrypted=cipher.encrypt(id,"synthetic-replacement-"+UUID.randomUUID());
        credentials.replace(id,"replacement",UUID.randomUUID().toString().replace("-","").substring(0,16),
                encrypted.keyVersion(),encrypted.nonce(),encrypted.ciphertext());
    }

    private ChannelProgress channel(Channel channel) {
        return reads.progress().channels().stream().filter(c -> c.channel()==channel).findFirst().orElseThrow();
    }
    private UUID credential(String label) {
        UUID id=UUID.randomUUID();String token="synthetic-local-only-"+UUID.randomUUID();
        var encrypted=cipher.encrypt(id,token);
        credentials.create(new GithubSystemCredential(id,label,id.toString().replace("-","").substring(0,16),encrypted.keyVersion(),
                encrypted.nonce(),encrypted.ciphertext(),"ACTIVE",null,null,Instant.now(),Instant.now()));
        assertThat(cipher.decrypt(id,encrypted.keyVersion(),encrypted.nonce(),encrypted.ciphertext())).isEqualTo(token);
        return id;
    }
    private GithubCredentialLeaseAppService.Claimed claim() {
        return claimOptional().orElseThrow();
    }
    private java.util.Optional<GithubCredentialLeaseAppService.Claimed> claimOptional() {
        scheduler.enqueueDue(Instant.now(),1);
        jdbc.update("UPDATE github_official_discovery_budget SET next_lease_eligible_at=clock_timestamp()-INTERVAL '1 second'");
        return claims.claim(GithubSyncTaskKind.DISCOVERY,"quota-fixture",platform(GithubOfficialDiscoveryCatalog.REQUIRED_CAPABILITIES));
    }
    private void releaseForNextClaim() {
        // Advance scheduling/lease clocks only; quota state and token choice stay production-controlled.
        jdbc.update("UPDATE github_sync_task SET lease_expires_at=clock_timestamp()-INTERVAL '1 second' WHERE status='LEASED'");
        jdbc.update("UPDATE github_sync_task SET due_at=clock_timestamp()-INTERVAL '1 second',retry_at=clock_timestamp()-INTERVAL '1 second' WHERE status='RETRY_WAIT'");
    }
    private void observe(GithubCredentialLeaseAppService.Claimed claimed,String state,Integer remaining,Integer retryAfter) {
        var lease=claimed.lease();Instant now=Instant.now();
        assertThat(claims.reportOfficialQuota(lease.task().taskId(),lease.task().leaseGeneration(),lease.token(),
                new GithubOfficialQuotaRepository.Observation("SEARCH",now,state,remaining,
                        remaining==null?null:now.plusSeconds(300),retryAfter))).isTrue();
    }
}
