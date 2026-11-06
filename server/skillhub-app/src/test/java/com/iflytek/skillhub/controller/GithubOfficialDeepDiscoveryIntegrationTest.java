package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.support.PostgresTestImage;
import static com.iflytek.skillhub.support.GithubSyncWorkerCapabilities.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.githubsync.*;
import com.iflytek.skillhub.service.GithubCredentialLeaseAppService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "skillhub.builtin-skills.enabled=false")
class GithubOfficialDeepDiscoveryIntegrationTest {
    private static final String BASE = "/api/v1/admin/github-sync/deep-discovery";
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PostgresTestImage.name());
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",POSTGRES::getUsername);
        registry.add("spring.datasource.password",POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        registry.add("spring.jpa.database-platform",()->"org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled",()->true);
        registry.add("spring.jpa.hibernate.ddl-auto",()->"validate");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired GithubOfficialDiscoveryRepository scheduler;
    @Autowired GithubSyncLeaseService leases;
    @Autowired GithubOfficialLifecycleRepository lifecycle;
    @Autowired GithubOfficialQuotaRepository quota;
    @Autowired GithubCredentialLeaseAppService credentialClaims;

    @BeforeEach void setup() {
        jdbc.update("UPDATE github_deep_discovery_config SET enabled=TRUE WHERE id");
        jdbc.update("UPDATE github_sync_admission_policy SET global_high=10000,global_low=5000,namespace_high=10000,namespace_low=5000 WHERE id");
    }

    @Test void adminSecurityCsrfCasAndBoundedReadQueries() throws Exception {
        mvc.perform(get(BASE+"/configuration")).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE+"/configuration").with(authentication(auth("USER")))).andExpect(status().isForbidden());
        String body="{\"expectedVersion\":1,\"enabled\":true,\"budgetProfile\":\"BALANCED\",\"exclusions\":[]}";
        mvc.perform(put(BASE+"/configuration").with(authentication(auth("USER"))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put(BASE+"/configuration").with(authentication(auth("SUPER_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(put(BASE+"/configuration").with(authentication(auth("SUPER_ADMIN"))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2));
        mvc.perform(put(BASE+"/configuration").with(authentication(auth("SUPER_ADMIN"))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        var lease=claim(); report(lease,null,checkpoint(lease,2,"IN_PROGRESS"),41,"allowed");
        for(String path:List.of("/repositories","/repositories/41","/repositories/41/provenance","/repositories/41/paths","/progress"))
            mvc.perform(get(BASE+path).with(authentication(auth("SUPER_ADMIN"))))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        for(int mask=0;mask<16;mask++) {
            var request=get(BASE+"/repositories").with(authentication(auth("SUPER_ADMIN")));
            if((mask&1)!=0) request.param("q","allowed");
            if((mask&2)!=0) request.param("status","PENDING");
            if((mask&4)!=0) request.param("channel",snapshot(lease).path("channel").asText());
            if((mask&8)!=0) request.param("paused","false");
            mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        }
        mvc.perform(get(BASE+"/repositories").param("channel",snapshot(lease).path("channel").asText())
                .param("paused","true").with(authentication(auth("SUPER_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get(BASE+"/summary").param("from",jdbc.queryForObject("SELECT available_since FROM github_discovery_metrics_epoch WHERE id",java.sql.Timestamp.class).toInstant().minusSeconds(1).toString())
                .param("until",Instant.now().toString()).with(authentication(auth("SUPER_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.recent.complete").value(false));
        mvc.perform(get(BASE+"/summary").param("from",jdbc.queryForObject("SELECT available_since FROM github_discovery_metrics_epoch WHERE id",java.sql.Timestamp.class).toInstant().toString())
                .param("until",Instant.now().toString()).with(authentication(auth("SUPER_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.recent.complete").value(true))
                .andExpect(jsonPath("$.data.recent.newCandidateRepositories").value(1))
                .andExpect(jsonPath("$.data.current.pendingRepositoryChecks").value(1));
        mvc.perform(get(BASE+"/repositories").param("page","1001").with(authentication(auth("SUPER_ADMIN"))))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE+"/repositories").param("size","101").with(authentication(auth("SUPER_ADMIN"))))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE+"/repositories").param("asOf",Instant.now().plusSeconds(600).toString()).with(authentication(auth("SUPER_ADMIN"))))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE+"/repositories/999999").with(authentication(auth("SUPER_ADMIN"))))
                .andExpect(status().isNotFound());
    }

    @Test void capabilityIsolationReplayConflictAndExclusionArePersisted() throws Exception {
        assertThat(scheduler.enqueueDue(Instant.now(),1)).isEqualTo(1);
        assertThat(leases.claim(GithubSyncTaskKind.DISCOVERY,"legacy", platform(java.util.List.of()))).isEmpty();
        var lease=leases.claim(GithubSyncTaskKind.DISCOVERY,"official",platform(GithubOfficialDiscoveryCatalog.REQUIRED_CAPABILITIES)).orElseThrow();
        String checkpoint=checkpoint(lease,2,"IN_PROGRESS");
        report(lease,null,checkpoint,42,"allowed");
        report(lease,null,checkpoint,42,"allowed");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_discovery_candidate WHERE repository_id=42",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_discovery_outcome_event WHERE event_type='CANDIDATE_CREATED' AND repository_id=42",Integer.class)).isEqualTo(1);
        assertThatThrownBy(()->report(lease,null,checkpoint,43,"allowed")).isInstanceOf(GithubOfficialProtocolException.class);
        jdbc.update("UPDATE github_deep_discovery_config SET exclusions_json='[{\"kind\":\"OWNER\",\"value\":\"excluded\"}]'::jsonb WHERE id");
        String next=checkpoint(lease,3,"IN_PROGRESS");
        report(lease,checkpoint,next,44,"excluded");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_discovery_candidate WHERE repository_id=44",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT discovery_checkpoint FROM github_sync_task WHERE id=?",String.class,lease.task().taskId())).isEqualTo(next);
        assertThat(jdbc.queryForObject("SELECT (official_metadata->>'excludedCount')::int FROM github_discovery_batch WHERE task_id=? AND checkpoint=?",Integer.class,lease.task().taskId(),next)).isEqualTo(1);
    }

    @Test void pauseRejectsOldLeaseAndResumesSavedCheckpoint() throws Exception {
        var lease=claim();String cp=checkpoint(lease,2,"IN_PROGRESS");report(lease,null,cp,45,"allowed");
        jdbc.update("UPDATE github_deep_discovery_config SET enabled=FALSE WHERE id");
        lifecycle.pauseDiscovery(lease.task().taskId(),lease.task().leaseGeneration(),lease.token(),cp);
        assertThatThrownBy(()->lifecycle.control(lease.task().taskId(),lease.task().leaseGeneration(),lease.token()))
                .isInstanceOf(GithubOfficialProtocolException.class);
        assertThatThrownBy(()->report(lease,cp,checkpoint(lease,3,"IN_PROGRESS"),46,"allowed"))
                .isInstanceOf(GithubOfficialProtocolException.class);
        assertThat(leases.claim(GithubSyncTaskKind.DISCOVERY,"official",platform(GithubOfficialDiscoveryCatalog.REQUIRED_CAPABILITIES))).isEmpty();
        jdbc.update("UPDATE github_deep_discovery_config SET enabled=TRUE WHERE id");
        jdbc.update("UPDATE github_official_discovery_budget SET next_lease_eligible_at=clock_timestamp()-INTERVAL '1 second'");
        var resumed=leases.claim(GithubSyncTaskKind.DISCOVERY,"official",platform(GithubOfficialDiscoveryCatalog.REQUIRED_CAPABILITIES)).orElseThrow();
        assertThat(resumed.task().taskId()).isEqualTo(lease.task().taskId());
        assertThat(resumed.task().leaseGeneration()).isGreaterThan(lease.task().leaseGeneration());
        assertThat(jdbc.queryForObject("SELECT discovery_checkpoint FROM github_sync_task WHERE id=?",String.class,resumed.task().taskId())).isEqualTo(cp);
    }

    @Test void primarySearchIsResourceScopedAndSecondaryIsSharedWithLeaseProof() throws Exception {
        var lease=claim();Instant now=Instant.now();String hash=hash(lease.token());
        var primary=new GithubOfficialQuotaRepository.Observation("SEARCH",now,"PRIMARY_EXHAUSTED",0,now.plusSeconds(120),null);
        assertThat(quota.record(lease.task().taskId(),lease.task().leaseGeneration(),hash,UUID.randomUUID(),primary,now)).isFalse();
        assertThat(quota.record(lease.task().taskId(),lease.task().leaseGeneration(),hash,null,primary,now)).isTrue();
        assertThat(quota.cooldown("SEARCH",now)).isAfter(now);
        assertThat(quota.cooldown("CORE",now)).isNull();
        assertThat(jdbc.queryForObject("SELECT observation_key FROM github_official_quota_observation",String.class)).isEqualTo("anonymous");
        queueCore(88001);
        assertThat(credentialClaims.claim(GithubSyncTaskKind.REPOSITORY_SYNC,"core-worker", platform(java.util.List.of()))).isPresent();
        assertThat(quota.record(lease.task().taskId(),lease.task().leaseGeneration(),hash,null,
                new GithubOfficialQuotaRepository.Observation("SEARCH",now.plusSeconds(1),"SECONDARY_COOLDOWN",null,null,60),now.plusSeconds(1))).isTrue();
        assertThat(quota.cooldown("CORE",now)).isAfter(now);
        UUID blocked=queueCore(88002);
        assertThat(credentialClaims.claim(GithubSyncTaskKind.REPOSITORY_SYNC,"core-worker", platform(java.util.List.of()))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT official_pause_reason FROM github_sync_task WHERE id=?",String.class,blocked)).isEqualTo("QUOTA_COOLDOWN");
        assertThat(quota.record(lease.task().taskId(),lease.task().leaseGeneration(),"a".repeat(64),null,primary,now)).isFalse();
    }

    @Test void configuredInvalidTokenDefersInsteadOfFallingBackToAnonymous() {
        jdbc.update("""
                INSERT INTO github_system_credential(id,label,fingerprint,key_version,nonce,ciphertext,status)
                VALUES (?,'fixture','0123456789abcdef','fixture',?,?,'INVALID')
                """,UUID.randomUUID(),new byte[12],new byte[17]);
        assertThat(scheduler.enqueueDue(Instant.now(),1)).isEqualTo(1);
        assertThat(credentialClaims.claim(GithubSyncTaskKind.DISCOVERY,"official",platform(GithubOfficialDiscoveryCatalog.REQUIRED_CAPABILITIES))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM github_sync_task WHERE official_query_id IS NOT NULL",String.class)).isEqualTo("RETRY_WAIT");
        assertThat(jdbc.queryForObject("SELECT official_pause_reason FROM github_sync_task WHERE official_query_id IS NOT NULL",String.class)).isEqualTo("CREDENTIAL_UNAVAILABLE");
        jdbc.update("DELETE FROM github_system_credential");
        jdbc.update("UPDATE github_sync_task SET due_at=CURRENT_TIMESTAMP-INTERVAL '1 second',retry_at=CURRENT_TIMESTAMP-INTERVAL '1 second'");
        assertThat(credentialClaims.claim(GithubSyncTaskKind.DISCOVERY,"official",platform(GithubOfficialDiscoveryCatalog.REQUIRED_CAPABILITIES))).isPresent();
        assertThat(jdbc.queryForObject("SELECT official_pause_reason FROM github_sync_task WHERE official_query_id IS NOT NULL",String.class)).isNull();
    }

    @Test void configuredDisabledTokenDefersProductionClaimWithoutAnonymousFallback() {
        jdbc.update("""
                INSERT INTO github_system_credential(id,label,fingerprint,key_version,nonce,ciphertext,status)
                VALUES (?,'disabled fixture','fedcba9876543210','fixture',?,?,'DISABLED')
                """, UUID.randomUUID(), new byte[12], new byte[17]);
        assertThat(scheduler.enqueueDue(Instant.now(), 1)).isEqualTo(1);
        assertThat(credentialClaims.claim(GithubSyncTaskKind.DISCOVERY, "official",
                platform(GithubOfficialDiscoveryCatalog.REQUIRED_CAPABILITIES))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM github_sync_task WHERE official_query_id IS NOT NULL", String.class))
                .isEqualTo("RETRY_WAIT");
        assertThat(jdbc.queryForObject("SELECT official_pause_reason FROM github_sync_task WHERE official_query_id IS NOT NULL", String.class))
                .isEqualTo("CREDENTIAL_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_sync_task WHERE official_query_id IS NOT NULL AND credential_id IS NOT NULL", Integer.class))
                .isZero();
    }

    @Test void excludedCandidatesStillConsumeBoundedOriginalResponseBudget() throws Exception {
        jdbc.update("UPDATE github_sync_admission_policy SET candidate_per_lease_limit=1 WHERE id");
        jdbc.update("UPDATE github_deep_discovery_config SET exclusions_json='[{\"kind\":\"OWNER\",\"value\":\"excluded\"}]'::jsonb WHERE id");
        var lease=claim();String cp=checkpoint(lease,2,"IN_PROGRESS");
        report(lease,null,cp,71,"excluded");
        report(lease,null,cp,71,"excluded");
        assertThatThrownBy(()->report(lease,cp,checkpoint(lease,3,"IN_PROGRESS"),72,"excluded"))
                .isInstanceOf(GithubOfficialProtocolException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_discovery_candidate",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_discovery_batch",Integer.class)).isEqualTo(1);
    }

    @Test void gapDoesNotBlockSiblingAndOnlyAllLeavesAdvanceCoverage() throws Exception {
        var root=claim();var snapshot=snapshot(root);UUID query=UUID.fromString(snapshot.path("queryId").asText());
        jdbc.update("UPDATE github_deep_discovery_query SET next_due_at=CURRENT_TIMESTAMP+INTERVAL '2 days' WHERE id<>?",query);
        Instant since=Instant.parse(snapshot.path("range").path("since").asText());
        Instant until=Instant.parse(snapshot.path("range").path("until").asText());
        Instant middle=since.plusSeconds(java.time.Duration.between(since,until).getSeconds()/2);
        String split=checkpoint(root,1,"SPLIT");
        var children=List.of(new GithubOfficialDiscoveryBatch.Range(since,middle,null),new GithubOfficialDiscoveryBatch.Range(middle,until,null));
        reportEmpty(root,null,split,"SPLIT",children,null);
        reportEmpty(root,null,split,"SPLIT",children,null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_official_discovery_shard",Integer.class)).isEqualTo(3);
        lifecycle.complete(root.task().taskId(),root.task().leaseGeneration(),root.token(),split,"COMPLETE",null);
        var first=claim();String gap=checkpoint(first,1,"GAP");
        reportEmpty(first,null,gap,"GAP",List.of(),"UNSPLITTABLE");
        assertThat(lifecycle.complete(first.task().taskId(),first.task().leaseGeneration(),first.token(),gap,"INCOMPLETE",Instant.now().plusSeconds(600))).isEqualTo("PARTIAL");
        var sibling=claim();assertThat(sibling.task().taskId()).isNotEqualTo(first.task().taskId());
        String complete=checkpoint(sibling,2,"COMPLETE");reportEmpty(sibling,null,complete,"COMPLETE",List.of(),null);
        lifecycle.complete(sibling.task().taskId(),sibling.task().leaseGeneration(),sibling.token(),complete,"COMPLETE",null);
        assertThat(jdbc.queryForObject("SELECT covered_until FROM github_deep_discovery_query WHERE id=?",java.sql.Timestamp.class,query)).isNull();
        jdbc.update("UPDATE github_official_discovery_shard SET retry_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE state='GAP'");
        jdbc.update("UPDATE github_deep_discovery_query SET next_due_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE id=?",query);
        var retry=claim();assertThat(retry.task().taskId()).isEqualTo(first.task().taskId());
        assertThat(retry.task().leaseGeneration()).isGreaterThan(first.task().leaseGeneration());
        String done=checkpoint(retry,2,"COMPLETE");reportEmpty(retry,retry.task().discoveryCheckpoint(),done,"COMPLETE",List.of(),null);
        lifecycle.complete(retry.task().taskId(),retry.task().leaseGeneration(),retry.token(),done,"COMPLETE",null);
        assertThat(jdbc.queryForObject("SELECT covered_until FROM github_deep_discovery_query WHERE id=?",java.sql.Timestamp.class,query).toInstant()).isEqualTo(until);
    }

    private void reportEmpty(GithubSyncLeaseService.LeasedTask lease,String previous,String cp,String coverage,
            List<GithubOfficialDiscoveryBatch.Range> children,String gap) {
        lifecycle.report(lease.task().taskId(),lease.task().leaseGeneration(),lease.token(),
                new GithubDiscoveryBatch(previous,cp,List.of(),0,null),
                new GithubOfficialDiscoveryBatch(lease.task().taskId(),coverage,1,children,gap));
    }

    private UUID queueCore(long repositoryId) {
        long namespace=jdbc.queryForObject("SELECT namespace_id FROM github_deep_discovery_config WHERE id",Long.class);
        Long source=jdbc.queryForObject("""
                INSERT INTO github_source_binding(namespace_id,repository_id,owner_login,repository_name,visibility)
                VALUES (?,?,'fixture','skills','PUBLIC') RETURNING id
                """,Long.class,namespace,repositoryId);
        UUID run=UUID.randomUUID(),task=UUID.randomUUID();
        jdbc.update("INSERT INTO github_sync_run(id,namespace_id,kind) VALUES (?,?,'REPOSITORY_SYNC')",run,namespace);
        jdbc.update("""
                INSERT INTO github_sync_task(id,run_id,namespace_id,source_id,kind,mode,dedupe_key,snapshot_json)
                VALUES (?,?,?,?,'REPOSITORY_SYNC','HEAD_ONLY',?,jsonb_build_object('repositoryId',?::bigint,'owner','fixture','name','skills'))
                """,task,run,namespace,source,task.toString(),repositoryId);
        return task;
    }

    private GithubSyncLeaseService.LeasedTask claim() {
        assertThat(scheduler.enqueueDue(Instant.now(),1)).isEqualTo(1);
        // Advance only pacing time in this checkpoint-focused fixture; budget tests verify real delays.
        jdbc.update("UPDATE github_official_discovery_budget SET next_lease_eligible_at=clock_timestamp()-INTERVAL '1 second'");
        return credentialClaims.claim(GithubSyncTaskKind.DISCOVERY,"official",platform(GithubOfficialDiscoveryCatalog.REQUIRED_CAPABILITIES)).orElseThrow().lease();
    }
    private JsonNode snapshot(GithubSyncLeaseService.LeasedTask lease) throws Exception {
        return mapper.readTree(lease.task().snapshotJson()).path("officialDiscovery");
    }
    private String checkpoint(GithubSyncLeaseService.LeasedTask lease,int page,String coverage) throws Exception {
        var result=mapper.createObjectNode();JsonNode snapshot=snapshot(lease);
        result.put("formatVersion",1).put("nextPage",page).put("coverage",coverage);
        for(String field:List.of("strategyVersion","queryId","queryVersion","shardId")) result.set(field,snapshot.get(field));
        return result.toString();
    }
    private void report(GithubSyncLeaseService.LeasedTask lease,String previous,String checkpoint,long repo,String owner) throws Exception {
        JsonNode s=snapshot(lease);
        var provenance=new GithubOfficialDiscoveryBatch.Provenance(s.path("channel").asText(),UUID.fromString(s.path("queryId").asText()),
                s.path("queryVersion").asLong(),s.path("strategyVersion").asText(),lease.task().taskId(),null);
        lifecycle.report(lease.task().taskId(),lease.task().leaseGeneration(),lease.token(),
                new GithubDiscoveryBatch(previous,checkpoint,List.of(new GithubDiscoveryCandidate(repo,owner,"skills","main",List.of(provenance))),0,null),
                new GithubOfficialDiscoveryBatch(lease.task().taskId(),"IN_PROGRESS",1,List.of(),null));
    }
    private String hash(String token) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
    private UsernamePasswordAuthenticationToken auth(String role) {
        var p=new PlatformPrincipal("github-sync-publisher","fixture",null,null,null,Set.of(role));
        return new UsernamePasswordAuthenticationToken(p,null,List.of(new SimpleGrantedAuthority("ROLE_"+role)));
    }
}
