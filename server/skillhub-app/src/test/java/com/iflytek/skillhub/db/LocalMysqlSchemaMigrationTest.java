package com.iflytek.skillhub.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LocalMysqlSchemaMigrationTest {

    @Test
    void localMysqlSchema_initializesRequiredBootstrapTablesWithoutPostgresSpecificTypes() throws IOException {
        String sql = Files.readString(mysqlInitMigration());

        assertThat(sql).contains("CREATE TABLE user_account");
        assertThat(sql).contains("CREATE TABLE role");
        assertThat(sql).contains("CREATE TABLE namespace");
        assertThat(sql).contains("CREATE TABLE local_credential");
        assertThat(sql).contains("CREATE TABLE user_role_binding");
        assertThat(sql).contains("CREATE TABLE namespace_member");

        assertThat(sql).doesNotContain("BIGSERIAL");
        assertThat(sql).doesNotContain("JSONB");
        assertThat(sql).doesNotContain("::jsonb");
    }

    private Path mysqlInitMigration() {
        return Path.of("")
                .toAbsolutePath()
                .getParent()
                .getParent()
                .resolve("server")
                .resolve("skillhub-app")
                .resolve("src/main/resources/sql/migration-mysql/V1__init_local_mysql_schema.sql");
    }
}
