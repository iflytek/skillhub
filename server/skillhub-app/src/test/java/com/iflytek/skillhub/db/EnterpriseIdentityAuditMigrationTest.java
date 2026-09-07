package com.iflytek.skillhub.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class EnterpriseIdentityAuditMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeEach
    void resetDatabase() {
        flyway(null).clean();
    }

    @Test
    void expandsLegacyAuditLogWithOrganizationCorrelationWithoutChangingOldRows()
            throws SQLException {
        Flyway legacy = flyway(MigrationVersion.fromVersion("60"));
        legacy.migrate();
        execute("""
                INSERT INTO audit_log (actor_user_id, action, target_type, target_id, request_id)
                VALUES (NULL, 'LEGACY_ACTION', 'SKILL', 42, 'legacy-request')
                """);

        Flyway current = flyway(MigrationVersion.fromVersion("61"));
        current.migrate();

        assertThat(current.info().current().getVersion().getVersion()).isEqualTo("61");
        assertThat(queryStrings("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'audit_log'
                  AND column_name IN ('organization_id', 'target_ref', 'result')
                ORDER BY column_name
                """)).containsExactly("organization_id", "result", "target_ref");
        assertThat(queryLong("""
                SELECT COUNT(*) FROM audit_log
                WHERE action = 'LEGACY_ACTION' AND target_id = 42
                  AND organization_id IS NULL AND target_ref IS NULL AND result IS NULL
                """)).isEqualTo(1);
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
            var values = new java.util.ArrayList<String>();
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
