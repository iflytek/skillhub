package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.support.PostgresTestImage;
import com.iflytek.skillhub.githubsync.GithubSystemCredential;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSystemCredentialRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class GithubSystemCredentialRepositoryTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(PostgresTestImage.name());

    @Test
    void quotaExhaustionDoesNotInvalidateCredentialButAuthenticationFailureDoes() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        var credentials = new JdbcGithubSystemCredentialRepository(jdbc);
        Instant now = Instant.now();
        UUID credentialId = UUID.randomUUID();
        String fingerprint = "a".repeat(16);
        credentials.create(new GithubSystemCredential(credentialId, "Public GitHub reader",
                fingerprint, "v1", new byte[12], new byte[17], "ACTIVE",
                null, null, now, now));
        long namespaceId = jdbc.queryForObject(
                "SELECT id FROM namespace WHERE slug = 'github'", Long.class);
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        String leaseTokenHash = "b".repeat(64);
        jdbc.update("INSERT INTO github_sync_run (id, namespace_id, kind) VALUES (?, ?, 'DISCOVERY')",
                runId, namespaceId);
        jdbc.update("""
                INSERT INTO github_sync_task
                    (id, run_id, namespace_id, kind, status, dedupe_key, snapshot_json,
                     lease_generation, lease_token_hash, lease_expires_at, leased_by)
                VALUES (?, ?, ?, 'DISCOVERY', 'LEASED', ?, '{}'::jsonb,
                        1, ?, ?, 'credential-worker')
                """, taskId, runId, namespaceId, "discovery:" + taskId,
                leaseTokenHash, Timestamp.from(now.plusSeconds(300)));
        assertThat(credentials.assignToLease(taskId, 1, credentialId, fingerprint, now)).isTrue();

        Instant resetAt = now.plusSeconds(3600);
        assertThat(credentials.markRateLimited(taskId, 1, leaseTokenHash,
                credentialId, resetAt, now)).isTrue();
        assertThat(credentials.recordQuotaForLease(taskId, 1, leaseTokenHash,
                credentialId, "SEARCH", 0, resetAt, now)).isTrue();
        assertThat(credentials.find(credentialId).orElseThrow().status()).isEqualTo("ACTIVE");
        assertThat(credentials.availableForClaim(now, "SEARCH")).isEmpty();
        assertThat(credentials.quotas(credentialId)).singleElement()
                .satisfies(quota -> assertThat(quota.remaining()).isZero());

        assertThat(credentials.markInvalid(taskId, 1, "c".repeat(64), credentialId, now)).isFalse();
        assertThat(credentials.markInvalid(taskId, 1, leaseTokenHash, credentialId, now)).isTrue();
        assertThat(credentials.find(credentialId).orElseThrow().status()).isEqualTo("INVALID");
        assertThat(credentials.availableForClaim(resetAt.plusSeconds(1), "SEARCH")).isEmpty();

        UUID depletedId = UUID.randomUUID();
        UUID healthyId = UUID.randomUUID();
        credentials.create(new GithubSystemCredential(depletedId, "Search exhausted",
                "d".repeat(16), "v1", new byte[12], new byte[17], "ACTIVE",
                null, null, now, now));
        credentials.create(new GithubSystemCredential(healthyId, "Healthy reader",
                "e".repeat(16), "v1", new byte[12], new byte[17], "ACTIVE",
                null, null, now, now));
        credentials.recordQuota(depletedId, "SEARCH", 0, resetAt, now);
        assertThat(credentials.availableForClaim(now, "SEARCH").map(GithubSystemCredential::id))
                .contains(healthyId);
        assertThat(credentials.setEnabled(healthyId, false)).isTrue();
        assertThat(credentials.availableForClaim(now, "SEARCH")).isEmpty();
        assertThat(credentials.availableForClaim(now, "CORE").map(GithubSystemCredential::id))
                .contains(depletedId);
        assertThat(credentials.availableForClaim(resetAt.plusSeconds(1), "SEARCH")
                .map(GithubSystemCredential::id)).contains(depletedId);
    }
}
