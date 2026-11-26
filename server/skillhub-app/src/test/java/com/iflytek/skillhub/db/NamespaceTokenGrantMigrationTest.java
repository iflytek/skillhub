package com.iflytek.skillhub.db;

import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class NamespaceTokenGrantMigrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(com.iflytek.skillhub.support.PostgresTestImage.name());

    @Test
    void existingAndRollingUpgradeIssuersRequireAnExplicitNamespaceGrant() throws Exception {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var stmt = connection.createStatement()) {
            stmt.execute("CREATE TEMP TABLE api_token (id int primary key, scope_json jsonb)");
            stmt.execute("""
                    INSERT INTO api_token VALUES (1, '["skill:read", " namespace:manage ", "namespace:read"]')
                    """);
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V129__explicit_namespace_token_grants.sql"));
            stmt.execute("""
                    INSERT INTO api_token (id, scope_json) VALUES (2, '["namespace:manage"]');
                    """);
            stmt.execute("""
                    INSERT INTO api_token VALUES (3, '["namespace:manage"]', true)
                    """);
            try (var rows = stmt.executeQuery("SELECT scope_json, namespace_scopes_issued FROM api_token ORDER BY id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("[\"skill:read\", \" namespace:manage \", \"namespace:read\"]");
                assertThat(rows.getBoolean(2)).isFalse();
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean(2)).isFalse();
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean(2)).isTrue();
            }
        }
    }
}
