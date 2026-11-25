package com.iflytek.skillhub.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.support.PostgresTestImage;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class LoginConnectionSecretMigrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(PostgresTestImage.name());

    @Test
    void upgradePreservesExistingRevisionAndConstrainsNewSecretBindingsToTheirConnection() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        migrate(dataSource, "127");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO user_account (id, display_name) VALUES ('oidc-admin', 'OIDC admin')");
        jdbc.update("""
                INSERT INTO organization (id, slug, display_name, created_by)
                VALUES ('org-first', 'org-first', 'First', 'oidc-admin'),
                       ('org-second', 'org-second', 'Second', 'oidc-admin')
                """);
        jdbc.update("""
                INSERT INTO login_connection (id, public_handle, organization_id, scope_type, display_name, adapter_key)
                VALUES ('connection-a', 'handle-first', 'org-first', 'ORGANIZATION', 'First', 'OIDC'),
                       ('connection-b', 'handle-second', 'org-second', 'ORGANIZATION', 'Second', 'OIDC')
                """);
        insertRevision(jdbc, "legacy-revision", "connection-a", 1, null);

        migrate(dataSource, "128");

        assertThat(jdbc.queryForObject("""
                SELECT secret_binding_version IS NULL FROM login_connection_revision
                WHERE id = 'legacy-revision'
                """, Boolean.class)).isTrue();
        assertThatThrownBy(() -> insertSecret(jdbc, "org-second", "connection-a"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_login_connection_secret_organization");
        insertSecret(jdbc, "org-first", "connection-a");
        insertRevision(jdbc, "new-revision", "connection-a", 2, 1L);
        assertThatThrownBy(() -> insertRevision(jdbc, "wrong-connection", "connection-b", 1, 1L))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_login_connection_revision_secret_binding");
    }

    private void insertSecret(JdbcTemplate jdbc, String organizationId, String connectionId) {
        jdbc.update("""
                INSERT INTO login_connection_secret_version
                    (organization_id, connection_id, binding_version, purpose, status, key_id, nonce, ciphertext,
                     created_by)
                VALUES (?, ?, 1, 'oidc-client-secret', 'AVAILABLE', 'test-key', ?, ?, 'oidc-admin')
                """, organizationId, connectionId, new byte[12], new byte[17]);
    }

    private void insertRevision(JdbcTemplate jdbc, String id, String connectionId, long revision, Long secretVersion) {
        jdbc.update("""
                INSERT INTO login_connection_revision
                    (id, connection_id, revision, adapter_contract_version, config_schema_version,
                     capabilities, typed_config, secret_binding_version)
                VALUES (?, ?, ?, '1.0', 1, '[]'::jsonb, '{}'::jsonb, ?)
                """, id, connectionId, revision, secretVersion);
    }

    private void migrate(DriverManagerDataSource dataSource, String target) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(target)).load().migrate();
    }
}
