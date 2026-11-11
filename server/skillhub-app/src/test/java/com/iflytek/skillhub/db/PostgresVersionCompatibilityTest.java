package com.iflytek.skillhub.db;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.iflytek.skillhub.support.PostgresTestImage;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.utility.MountableFile;

/**
 * Proves the bundled Flyway runs on the PostgreSQL major selected by {@link PostgresTestImage}
 * and accepts schema history written by the Flyway 9.22.3 engine shipped in earlier releases.
 */
class PostgresVersionCompatibilityTest {
    /** Flyway engine bundled by releases before PostgreSQL 17 support (Spring Boot 3.2 default). */
    private static final String LEGACY_FLYWAY_IMAGE = "flyway/flyway:9.22.3";
    private static final String LEGACY_TARGET = "100";
    private static final Logger FLYWAY_LOGGER = (Logger) LoggerFactory.getLogger("org.flywaydb");
    private static final String HISTORY =
            "SELECT version,description,type,script,checksum FROM flyway_schema_history ORDER BY installed_rank";

    @Test
    void bundledFlywaySupportsTheConfiguredPostgresMajor() {
        try (var postgres = new PostgreSQLContainer<>(PostgresTestImage.name())) {
            postgres.start();
            var source = dataSource(postgres);
            int major = new JdbcTemplate(source).queryForObject("SHOW server_version_num", Integer.class) / 10000;
            PostgresTestImage.expectedMajor().ifPresent(expected -> assertThat(major).isEqualTo(expected));

            var flywayLogs = captureFlywayLogs();
            try {
                var flyway = flyway(source).load();
                assertThat(flyway.migrate().success).isTrue();
                flyway.validate();
                assertThat(flyway.info().pending()).isEmpty();
                assertThat(flyway.info().applied()).allMatch(info -> info.getState() == MigrationState.SUCCESS);
            } finally {
                FLYWAY_LOGGER.detachAppender(flywayLogs);
                FLYWAY_LOGGER.setLevel(null);
            }
            // Flyway 9.22.3 reported every PostgreSQL newer than 15 as untested; 10.20+ supports 16 and 17.
            assertThat(flywayLogs.list).extracting(ILoggingEvent::getFormattedMessage)
                    .noneMatch(message -> message.contains("Flyway upgrade recommended"));
        }
    }

    @Test
    void historyAndDataFromLegacyFlywaySurviveUpgradeAndValidate() {
        try (var postgres = new PostgreSQLContainer<>(PostgresTestImage.name())) {
            postgres.start();
            runLegacyFlyway(postgres);

            var source = dataSource(postgres);
            var jdbc = new JdbcTemplate(source);
            assertThat(jdbc.queryForObject("SELECT max(version::int) FROM flyway_schema_history", Integer.class))
                    .isEqualTo(Integer.parseInt(LEGACY_TARGET));
            List<Map<String, Object>> legacyHistory = jdbc.queryForList(HISTORY);

            jdbc.update("INSERT INTO user_account(id,display_name) VALUES ('legacy-owner','Legacy owner')");
            long namespace = jdbc.queryForObject("INSERT INTO namespace(slug,display_name,type,created_by) "
                    + "VALUES ('legacy-team','Legacy team','TEAM','legacy-owner') RETURNING id", Long.class);
            long skill = jdbc.queryForObject("INSERT INTO skill(namespace_id,slug,owner_id,display_name,summary,visibility) "
                    + "VALUES (?,'legacy-skill','legacy-owner','Legacy skill','Created before the upgrade','PUBLIC') RETURNING id",
                    Long.class, namespace);
            long version = jdbc.queryForObject("INSERT INTO skill_version(skill_id,version,status) "
                    + "VALUES (?,'1.0.0','PUBLISHED') RETURNING id", Long.class, skill);
            jdbc.update("UPDATE skill SET latest_version_id=? WHERE id=?", version, skill);

            var flyway = flyway(source).load();
            // Bundled Flyway must accept checksums written by 9.22.3 before applying anything new.
            flyway(source).target(LEGACY_TARGET).load().validate();
            var result = flyway.migrate();
            assertThat(result.success).isTrue();
            assertThat(result.migrationsExecuted).isPositive();
            flyway.validate();

            assertThat(jdbc.queryForList(HISTORY).subList(0, legacyHistory.size())).isEqualTo(legacyHistory);
            MigrationInfo current = flyway.info().current();
            assertThat(current.getVersion().getVersion()).isEqualTo(latestMigrationVersion(flyway));
            assertThat(flyway.info().pending()).isEmpty();
            assertThat(jdbc.queryForMap("SELECT display_name,latest_version_id FROM skill WHERE id=?", skill))
                    .containsEntry("display_name", "Legacy skill").containsEntry("latest_version_id", version);
            assertThat(jdbc.queryForObject("SELECT status FROM skill_version WHERE id=?", String.class, version))
                    .isEqualTo("PUBLISHED");
            assertThat(jdbc.queryForObject("SELECT created_by FROM namespace WHERE id=?", String.class, namespace))
                    .isEqualTo("legacy-owner");
        }
    }

    private static ListAppender<ILoggingEvent> captureFlywayLogs() {
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        // logback-test.xml keeps the root at ERROR; Flyway reports unsupported versions at WARN.
        FLYWAY_LOGGER.setLevel(Level.WARN);
        FLYWAY_LOGGER.addAppender(appender);
        return appender;
    }

    private static void runLegacyFlyway(PostgreSQLContainer<?> postgres) {
        // Both containers share Docker's default bridge network; no extra network is created.
        String postgresIp = postgres.getContainerInfo().getNetworkSettings().getIpAddress();
        try (var legacy = new GenericContainer<>(LEGACY_FLYWAY_IMAGE)
                .withCopyFileToContainer(MountableFile.forClasspathResource("db/migration"), "/flyway/sql")
                .withCommand("-url=jdbc:postgresql://" + postgresIp + ":5432/" + postgres.getDatabaseName(),
                        "-user=" + postgres.getUsername(), "-password=" + postgres.getPassword(),
                        "-locations=filesystem:/flyway/sql", "-target=" + LEGACY_TARGET, "migrate")
                .withStartupCheckStrategy(new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(3)))) {
            legacy.start();
            assertThat(legacy.getLogs()).contains("Flyway Community Edition 9.22.3");
        }
    }

    private static String latestMigrationVersion(Flyway flyway) {
        MigrationInfo[] all = flyway.info().all();
        return all[all.length - 1].getVersion().getVersion();
    }

    private static org.flywaydb.core.api.configuration.FluentConfiguration flyway(DriverManagerDataSource source) {
        return Flyway.configure().dataSource(source).locations("classpath:db/migration");
    }

    private static DriverManagerDataSource dataSource(PostgreSQLContainer<?> postgres) {
        return new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
