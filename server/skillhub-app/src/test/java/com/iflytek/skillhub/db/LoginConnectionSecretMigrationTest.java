package com.iflytek.skillhub.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class LoginConnectionSecretMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeEach
    void resetDatabase() {
        flyway(null).clean();
    }

    @Test
    void addsEncryptedVersionedSecretStorageAndBindsRevisionsToExactVersions()
            throws SQLException {
        migrateAndSeedConnections();

        Flyway current = flyway(MigrationVersion.fromVersion("66"));
        current.migrate();

        assertThat(current.info().current().getVersion().getVersion()).isEqualTo("66");
        assertThat(queryStrings("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'login_connection_secret_version'
                ORDER BY ordinal_position
                """)).contains(
                        "organization_id",
                        "connection_id",
                        "purpose",
                        "binding_version",
                        "algorithm",
                        "key_id",
                        "nonce",
                        "ciphertext",
                        "status",
                        "valid_until"
                ).doesNotContain("plaintext", "value", "client_secret");

        execute(secretInsert("secret-a", "org-a", "connection-a", 1, "CURRENT"));
        execute("""
                INSERT INTO login_connection_revision (
                    id, connection_id, revision, adapter_contract_version,
                    config_schema_version, capabilities, typed_config,
                    secret_binding_version, created_by
                ) VALUES (
                    'revision-a', 'connection-a', 1, '1.0', 1,
                    '["IDENTITY_ASSERTION"]', '{}', 1, 'creator'
                )
                """);

        assertThat(queryLong("""
                SELECT COUNT(*)
                FROM login_connection_revision
                WHERE connection_id = 'connection-a' AND secret_binding_version = 1
                """)).isEqualTo(1);
        assertThatThrownBy(() -> execute("""
                INSERT INTO login_connection_revision (
                    id, connection_id, revision, adapter_contract_version,
                    config_schema_version, capabilities, typed_config,
                    secret_binding_version, created_by
                ) VALUES (
                    'revision-missing-secret', 'connection-a', 2, '1.0', 1,
                    '["IDENTITY_ASSERTION"]', '{}', 99, 'creator'
                )
                """)).isInstanceOf(SQLException.class);

        execute("""
                INSERT INTO login_connection (
                    id, public_handle, scope_type, organization_id, system_key,
                    display_name, status, adapter_key
                ) VALUES (
                    'platform-github', 'platform-github-handle', 'PLATFORM', NULL, 'github',
                    'GitHub', 'DRAFT', 'oauth'
                )
                """);
        execute("""
                INSERT INTO login_connection_secret_version (
                    id, organization_id, connection_id, purpose, binding_version,
                    algorithm, key_id, nonce, ciphertext, status, valid_until, created_by
                ) VALUES (
                    'platform-secret', NULL, 'platform-github', 'login.client-secret', 1,
                    'AES-256-GCM', 'key-2026-09', decode(repeat('01', 12), 'hex'),
                    decode(repeat('02', 32), 'hex'), 'CURRENT', NULL, 'creator'
                )
                """);
        assertThat(queryLong("""
                SELECT COUNT(*)
                FROM login_connection_secret_version
                WHERE connection_id = 'platform-github' AND scope_key = '@platform'
                """)).isEqualTo(1);
    }

    @Test
    void rejectsCrossTenantSecretsAndMultipleCurrentOrRetiringVersions() throws SQLException {
        migrateAndSeedConnections();
        flyway(MigrationVersion.fromVersion("66")).migrate();

        assertThatThrownBy(() -> execute(
                secretInsert("cross-org", "org-b", "connection-a", 1, "CURRENT")
        )).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("""
                INSERT INTO login_connection_secret_version (
                    id, organization_id, connection_id, purpose, binding_version,
                    algorithm, key_id, nonce, ciphertext, status, valid_until, created_by
                ) VALUES (
                    'cross-platform', NULL, 'connection-a', 'login.client-secret', 1,
                    'AES-256-GCM', 'key-2026-09', decode(repeat('01', 12), 'hex'),
                    decode(repeat('02', 32), 'hex'), 'CURRENT', NULL, 'creator'
                )
                """)).isInstanceOf(SQLException.class);

        execute(secretInsert("current-a", "org-a", "connection-a", 1, "CURRENT"));
        assertThatThrownBy(() -> execute(
                secretInsert("current-b", "org-a", "connection-a", 2, "CURRENT")
        )).isInstanceOf(SQLException.class);

        execute("""
                UPDATE login_connection_secret_version
                SET status = 'RETIRING', valid_until = CURRENT_TIMESTAMP + INTERVAL '1 hour'
                WHERE id = 'current-a'
                """);
        assertThatThrownBy(() -> execute(
                secretInsert("retiring-b", "org-a", "connection-a", 2, "RETIRING")
        )).isInstanceOf(SQLException.class);
    }

    private void migrateAndSeedConnections() throws SQLException {
        flyway(MigrationVersion.fromVersion("65")).migrate();
        execute("""
                INSERT INTO user_account (id, display_name, status)
                VALUES ('creator', 'Creator', 'ACTIVE')
                """);
        execute("""
                INSERT INTO organization (id, slug, display_name, created_by)
                VALUES
                    ('org-a', 'org-a', 'Organization A', 'creator'),
                    ('org-b', 'org-b', 'Organization B', 'creator')
                """);
        execute(connectionInsert("connection-a", "org-a"));
        execute(connectionInsert("connection-b", "org-b"));
    }

    private String connectionInsert(String id, String organizationId) {
        return """
                INSERT INTO login_connection (
                    id, public_handle, scope_type, organization_id,
                    display_name, status, adapter_key
                ) VALUES (
                    '%s', '%s-handle', 'ORGANIZATION', '%s', 'OIDC', 'DRAFT', 'oidc'
                )
                """.formatted(id, id, organizationId);
    }

    private String secretInsert(
            String id,
            String organizationId,
            String connectionId,
            long bindingVersion,
            String status
    ) {
        String validUntil = status.equals("RETIRING")
                ? "CURRENT_TIMESTAMP + INTERVAL '1 hour'"
                : "NULL";
        return """
                INSERT INTO login_connection_secret_version (
                    id, organization_id, connection_id, purpose, binding_version,
                    algorithm, key_id, nonce, ciphertext, status, valid_until, created_by
                ) VALUES (
                    '%s', '%s', '%s', 'login.client-secret', %d,
                    'AES-256-GCM', 'key-2026-09', decode(repeat('01', 12), 'hex'),
                    decode(repeat('02', 32), 'hex'), '%s', %s, 'creator'
                )
                """.formatted(
                id,
                organizationId,
                connectionId,
                bindingVersion,
                status,
                validUntil
        );
    }

    private Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long queryLong(String sql) throws SQLException {
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private List<String> queryStrings(String sql) throws SQLException {
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            var values = new ArrayList<String>();
            while (result.next()) {
                values.add(result.getString(1));
            }
            return List.copyOf(values);
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
    }
}
