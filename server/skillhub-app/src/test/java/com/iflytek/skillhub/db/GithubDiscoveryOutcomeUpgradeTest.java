package com.iflytek.skillhub.db;

import com.iflytek.skillhub.support.PostgresTestImage;
import com.iflytek.skillhub.infra.jpa.JdbcGithubDiscoveryOutcomeRepository;
import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class GithubDiscoveryOutcomeUpgradeTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PostgresTestImage.name());

    @Test void upgradingV114PreservesUnknownValidationForBothLegacyCandidatesAndSourceOnlyRepositories() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        migrate(source, "114");
        var jdbc = new JdbcTemplate(source);
        long namespace = jdbc.queryForObject("SELECT namespace_id FROM github_deep_discovery_config WHERE id", Long.class);
        jdbc.update("INSERT INTO github_discovery_candidate(namespace_id,repository_id,owner_login,repository_name) VALUES (?,9101,'legacy','candidate')", namespace);
        jdbc.update("INSERT INTO github_source_binding(namespace_id,repository_id,owner_login,repository_name,visibility) VALUES (?,9102,'legacy','source','PUBLIC')", namespace);
        jdbc.update("INSERT INTO github_discovery_candidate(namespace_id,repository_id,owner_login,repository_name) VALUES (?,9103,'legacy','proven')", namespace);
        jdbc.update("INSERT INTO github_official_validated_seed(namespace_id,repository_id,github_owner_id,owner_login,ingestion_id,validated_at) VALUES (?,9103,93,'legacy',gen_random_uuid(),now()-INTERVAL '1 day')", namespace);
        migrate(source, "115");
        assertThat(jdbc.queryForList("SELECT repository_id FROM github_discovery_legacy_unknown_validation ORDER BY repository_id", Long.class)).containsExactly(9101L, 9102L);
        assertThat(jdbc.queryForObject("SELECT occurred_at IS NULL FROM github_discovery_outcome_event WHERE repository_id=9103 AND event_type='FIRST_VALID'", Boolean.class)).isTrue();
        var outcomes = new JdbcGithubDiscoveryOutcomeRepository(jdbc);
        for (long repository : new long[]{9101, 9102, 9103, 9104}) {
            outcomes.firstValid(namespace, repository, Instant.now());
            outcomes.firstValid(namespace, repository, Instant.now());
        }
        assertThat(jdbc.queryForList("SELECT repository_id FROM github_discovery_outcome_event WHERE event_type='FIRST_VALID' AND NOT first_occurrence_known ORDER BY repository_id", Long.class)).containsExactly(9101L, 9102L);
        assertThat(jdbc.queryForObject("SELECT first_occurrence_known AND occurred_at IS NOT NULL FROM github_discovery_outcome_event WHERE repository_id=9104 AND event_type='FIRST_VALID'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT occurred_at IS NULL AND NOT official_at_event FROM github_discovery_outcome_event WHERE repository_id=9103 AND event_type='FIRST_VALID'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_discovery_outcome_event WHERE event_type='FIRST_VALID'", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT occurred_at IS NULL AND NOT official_at_event FROM github_discovery_outcome_event WHERE repository_id=9101 AND event_type='CANDIDATE_CREATED'", Boolean.class)).isTrue();
    }

    private void migrate(DriverManagerDataSource source, String target) {
        Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(target)).load().migrate();
    }
}
