package com.iflytek.skillhub.db;

import com.iflytek.skillhub.support.PostgresTestImage;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/** Proves the released main schema and its existing authoring/review data survive the sync upgrade. */
@Testcontainers
class GithubMainUpgradeCompatibilityTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PostgresTestImage.name());

    @Test
    void releasedMainDataAndChecksumsSurviveAllSyncMigrations() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("75").load().migrate();
        var jdbc = new JdbcTemplate(source);
        var releasedHistory = jdbc.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE version::int <= 75 ORDER BY installed_rank");
        jdbc.update("INSERT INTO user_account(id,display_name) VALUES ('main-upgrade-owner','Main owner')");
        long namespace = jdbc.queryForObject("INSERT INTO namespace(slug,display_name,type,created_by) VALUES ('main-upgrade','Main team','TEAM','main-upgrade-owner') RETURNING id", Long.class);
        long skill = jdbc.queryForObject("INSERT INTO skill(namespace_id,slug,owner_id,display_name,summary,visibility,active_avatar_id,active_avatar_audience) VALUES (?,'existing','main-upgrade-owner','Published title','Published summary','PRIVATE',777,'PRIVATE') RETURNING id", Long.class, namespace);
        long version = jdbc.queryForObject("INSERT INTO skill_version(skill_id,version,status) VALUES (?,'1.2.3','PUBLISHED') RETURNING id", Long.class, skill);
        jdbc.update("UPDATE skill SET latest_version_id=? WHERE id=?", version, skill);
        long review = jdbc.queryForObject("INSERT INTO review_task(skill_id,namespace_id,subject_type,subject_id,subject_version,submitted_by,previous_visibility,requested_visibility,visibility_version_id) VALUES (?,?,'SKILL_METADATA',?,'visibility:main-fixture','main-upgrade-owner','PRIVATE','PUBLIC',?) RETURNING id", Long.class, skill, namespace, skill, version);
        jdbc.update("INSERT INTO skill_draft(namespace_id,owner_id,name,content_digest) VALUES (?,'main-upgrade-owner','Existing draft',?)", namespace, "a".repeat(64));
        jdbc.update("INSERT INTO namespace_invitation_history(id,namespace_id,issued_by,issued_at,expires_at) VALUES ('main-invitation-history',?,'main-upgrade-owner',now(),now()+interval '1 day')", namespace);
        jdbc.update("INSERT INTO namespace_invitation(namespace_id,token_hash,issued_by,issued_at,expires_at,history_id) VALUES (?,?,'main-upgrade-owner',now(),now()+interval '1 day','main-invitation-history')", namespace, "b".repeat(64));

        var flyway = Flyway.configure().dataSource(source).locations("classpath:db/migration").load();
        flyway.migrate();
        flyway.validate();

        assertThat(jdbc.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE version::int <= 75 ORDER BY installed_rank")).isEqualTo(releasedHistory);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success AND version::int BETWEEN 76 AND 119", Integer.class)).isEqualTo(44);
        assertThat(jdbc.queryForObject("SELECT display_name FROM skill WHERE id=?", String.class, skill)).isEqualTo("Published title");
        assertThat(jdbc.queryForObject("SELECT active_avatar_id FROM skill WHERE id=?", Long.class, skill)).isEqualTo(777L);
        assertThat(jdbc.queryForObject("SELECT latest_version_id FROM skill WHERE id=?", Long.class, skill)).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT github_sync_managed FROM skill WHERE id=?", Boolean.class, skill)).isFalse();
        assertThat(jdbc.queryForMap("SELECT previous_visibility,requested_visibility,visibility_version_id,origin_type FROM review_task WHERE id=?", review))
                .containsEntry("previous_visibility", "PRIVATE").containsEntry("requested_visibility", "PUBLIC")
                .containsEntry("visibility_version_id", version).containsEntry("origin_type", "USER");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM skill_draft WHERE name='Existing draft'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT history_id FROM namespace_invitation WHERE namespace_id=?", String.class, namespace)).isEqualTo("main-invitation-history");
        assertThat(jdbc.queryForObject("SELECT type FROM namespace WHERE slug='github'", String.class)).isEqualTo("CATALOG");
    }
}
