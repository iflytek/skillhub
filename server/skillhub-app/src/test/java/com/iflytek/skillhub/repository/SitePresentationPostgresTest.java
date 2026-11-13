package com.iflytek.skillhub.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.auth.settings.SystemSettingRepository;
import com.iflytek.skillhub.service.SitePresentationService;
import com.iflytek.skillhub.support.PostgresTestImage;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class SitePresentationPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PostgresTestImage.name());

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired SystemSettingRepository settings;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired jakarta.persistence.EntityManager entityManager;

    @Test
    void migrationSeedsOneRowAndDoesNotResetCustomSettings() throws Exception {
        var stored = settings.findBySettingKey(SitePresentationService.SETTING_KEY).orElseThrow();
        stored.update(Map.of("siteName", "Persist across upgrades", "footerVisible", false), "admin");
        settings.saveAndFlush(stored);
        long version = stored.getVersion();
        String seed = new ClassPathResource("db/migration/V122__site_presentation_settings.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        jdbc.execute(seed);
        entityManager.clear();
        var reloaded = settings.findBySettingKey(SitePresentationService.SETTING_KEY).orElseThrow();
        assertThat(reloaded.getValue()).containsEntry("siteName", "Persist across upgrades").containsEntry("footerVisible", false);
        assertThat(reloaded.getVersion()).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM system_setting WHERE setting_key = ?", Long.class,
                SitePresentationService.SETTING_KEY)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT jsonb_typeof(value_json) FROM system_setting WHERE setting_key = ?", String.class,
                SitePresentationService.SETTING_KEY)).isEqualTo("object");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void upgradeFromPreviousSchemaPreservesExistingSettingsAndAddsPresentationDefaults() throws Exception {
        String schema = "site_presentation_upgrade";
        var previous = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("121").load();
        previous.migrate();
        try (var connection = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO " + schema + ".system_setting(setting_key, value_json) "
                    + "VALUES ('auth.local', '{\"passwordLoginEnabled\":false,\"selfRegistrationEnabled\":false}'::jsonb) "
                    + "ON CONFLICT (setting_key) DO UPDATE SET value_json=EXCLUDED.value_json");
            // Pin the upgrade to V122 so later migrations do not change what this upgrade step proves.
            var upgraded = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("122").load();
            assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);
            try (var rows = statement.executeQuery("SELECT value_json->>'passwordLoginEnabled' FROM " + schema
                    + ".system_setting WHERE setting_key='auth.local'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("false");
            }
            try (var rows = statement.executeQuery("SELECT value_json::text FROM " + schema
                    + ".system_setting WHERE setting_key='site.presentation'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("{}");
                assertThat(rows.next()).isFalse();
            }
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentWritersCannotSilentlyOverwriteCommittedPresentation() throws Exception {
        var transaction = new TransactionTemplate(transactionManager);
        var bothRead = new CountDownLatch(2);
        var firstCommitted = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                try {
                    transaction.executeWithoutResult(status -> {
                        var stored = settings.findBySettingKey(SitePresentationService.SETTING_KEY).orElseThrow();
                        bothRead.countDown();
                        await(bothRead);
                        stored.update(Map.of("siteName", "First administrator"), "admin-one");
                        settings.saveAndFlush(stored);
                    });
                } finally {
                    firstCommitted.countDown();
                }
            });
            var second = executor.submit(() -> {
                try {
                    transaction.executeWithoutResult(status -> {
                        var stale = settings.findBySettingKey(SitePresentationService.SETTING_KEY).orElseThrow();
                        bothRead.countDown();
                        await(firstCommitted);
                        stale.update(Map.of("siteName", "Stale administrator"), "admin-two");
                        settings.saveAndFlush(stale);
                    });
                    return null;
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            first.get(20, TimeUnit.SECONDS);
            assertThat(second.get(20, TimeUnit.SECONDS)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
            transaction.executeWithoutResult(status -> assertThat(settings.findBySettingKey(SitePresentationService.SETTING_KEY)
                    .orElseThrow().getValue()).containsEntry("siteName", "First administrator"));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out coordinating writers");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
