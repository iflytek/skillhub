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

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class GithubCatalogNamespaceMigrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(PostgresTestImage.name());

    @Test
    void newDeploymentCreatesOwnedCatalogAndSystemActor() {
        var fixture = beforeCatalogMigration("github_catalog_clean");

        migrate(fixture.source(), fixture.schema(), "76");

        assertThat(fixture.jdbc().queryForObject("""
                SELECT type FROM namespace WHERE slug = 'github'
                """, String.class)).isEqualTo("CATALOG");
        assertThat(fixture.jdbc().queryForObject("""
                SELECT created_by FROM namespace WHERE slug = 'github'
                """, String.class)).isEqualTo("github-sync-publisher");
        assertThat(fixture.jdbc().queryForObject("""
                SELECT system_account FROM user_account WHERE id = 'github-sync-publisher'
                """, Boolean.class)).isTrue();
        assertThat(fixture.jdbc().queryForObject("""
                SELECT member.role FROM namespace_member member
                JOIN namespace ON namespace.id = member.namespace_id
                WHERE namespace.slug = 'github' AND member.user_id = 'github-sync-publisher'
                """, String.class)).isEqualTo("OWNER");
    }

    @Test
    void existingGithubNamespaceBlocksMigrationWithoutAdoptingUserData() {
        var fixture = beforeCatalogMigration("github_slug_collision");
        fixture.jdbc().update("""
                INSERT INTO user_account (id, display_name) VALUES ('collision-owner', 'Existing Owner')
                """);
        fixture.jdbc().update("""
                INSERT INTO namespace (slug, display_name, type, created_by)
                VALUES ('github', 'Existing Team', 'TEAM', 'collision-owner')
                """);

        assertThatThrownBy(() -> migrate(fixture.source(), fixture.schema(), "76"))
                .hasStackTraceContaining("Cannot create @github catalog: namespace slug github already exists");
        assertThat(fixture.jdbc().queryForObject("""
                SELECT display_name FROM namespace WHERE slug = 'github'
                """, String.class)).isEqualTo("Existing Team");
        assertThat(fixture.jdbc().queryForObject("""
                SELECT type FROM namespace WHERE slug = 'github'
                """, String.class)).isEqualTo("TEAM");
        assertThat(fixture.jdbc().queryForObject("""
                SELECT count(*) FROM user_account WHERE id = 'github-sync-publisher'
                """, Integer.class)).isZero();
        assertThat(fixture.jdbc().queryForObject("""
                SELECT count(*) FROM flyway_schema_history WHERE version = '76' AND success = TRUE
                """, Integer.class)).isZero();
    }

    @Test
    void existingActorBlocksMigrationWithoutPromotingThatAccount() {
        var fixture = beforeCatalogMigration("github_actor_collision");
        fixture.jdbc().update("""
                INSERT INTO user_account (id, display_name, system_account)
                VALUES ('github-sync-publisher', 'Existing User', FALSE)
                """);

        assertThatThrownBy(() -> migrate(fixture.source(), fixture.schema(), "76"))
                .hasStackTraceContaining("Cannot create GitHub catalog actor: user id github-sync-publisher already exists");
        assertThat(fixture.jdbc().queryForObject("""
                SELECT display_name FROM user_account WHERE id = 'github-sync-publisher'
                """, String.class)).isEqualTo("Existing User");
        assertThat(fixture.jdbc().queryForObject("""
                SELECT system_account FROM user_account WHERE id = 'github-sync-publisher'
                """, Boolean.class)).isFalse();
        assertThat(fixture.jdbc().queryForObject("""
                SELECT count(*) FROM namespace WHERE slug = 'github'
                """, Integer.class)).isZero();
        assertThat(fixture.jdbc().queryForObject("""
                SELECT count(*) FROM flyway_schema_history WHERE version = '76' AND success = TRUE
                """, Integer.class)).isZero();
    }

    private Fixture beforeCatalogMigration(String schema) {
        var admin = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        admin.execute("CREATE SCHEMA " + schema);
        String url = POSTGRES.getJdbcUrl()
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?")
                + "currentSchema=" + schema;
        DataSource source = new DriverManagerDataSource(
                url, POSTGRES.getUsername(), POSTGRES.getPassword());
        migrate(source, schema, "75");
        return new Fixture(schema, source, new JdbcTemplate(source));
    }

    private void migrate(DataSource source, String schema, String target) {
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(target)).load().migrate();
    }

    private record Fixture(String schema, DataSource source, JdbcTemplate jdbc) {}
}
