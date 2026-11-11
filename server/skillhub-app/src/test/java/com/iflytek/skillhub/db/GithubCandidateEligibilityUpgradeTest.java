package com.iflytek.skillhub.db;

import com.iflytek.skillhub.support.PostgresTestImage;
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
class GithubCandidateEligibilityUpgradeTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PostgresTestImage.name());

    @Test void upgradingV117PreservesViewContractAndExistingEligibility() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        migrate(source, "117");
        var jdbc = new JdbcTemplate(source);
        long namespace = jdbc.queryForObject("SELECT namespace_id FROM github_deep_discovery_config WHERE id", Long.class);
        jdbc.update("UPDATE github_deep_discovery_config SET enabled=TRUE,exclusions_json='[{\"kind\":\"OWNER\",\"value\":\"blocked\"}]' WHERE id");
        jdbc.update("INSERT INTO github_discovery_candidate(namespace_id,repository_id,owner_login,repository_name) VALUES (?,9101,'Blocked','synthetic')", namespace);
        jdbc.update("INSERT INTO github_discovery_candidate_origin(candidate_id,origin_type,origin_key) SELECT id,'OFFICIAL_DISCOVERY',gen_random_uuid()::text FROM github_discovery_candidate");
        var before = jdbc.queryForList("SELECT * FROM github_candidate_discovery_eligibility ORDER BY candidate_id");
        String columns = "SELECT column_name,data_type,ordinal_position FROM information_schema.columns WHERE table_schema='public' AND table_name='github_candidate_discovery_eligibility' ORDER BY ordinal_position";
        var shape = jdbc.queryForList(columns);
        migrate(source, "118");
        assertThat(jdbc.queryForList(columns)).isEqualTo(shape);
        assertThat(jdbc.queryForList("SELECT * FROM github_candidate_discovery_eligibility ORDER BY candidate_id")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT acquisition_allowed FROM github_candidate_discovery_eligibility WHERE repository_id=9101", Boolean.class)).isFalse();
        jdbc.update("UPDATE github_discovery_candidate SET owner_login='allowed' WHERE repository_id=9101");
        assertThat(jdbc.queryForObject("SELECT acquisition_allowed FROM github_candidate_discovery_eligibility WHERE repository_id=9101", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version='118'", Boolean.class)).isTrue();
    }

    private void migrate(DriverManagerDataSource source, String target) {
        Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(target)).load().migrate();
    }
}
