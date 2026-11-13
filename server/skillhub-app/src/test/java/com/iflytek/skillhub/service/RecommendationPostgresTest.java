package com.iflytek.skillhub.service;
import com.iflytek.skillhub.repository.RecommendationSkillQuery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.service.*;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import jakarta.validation.Validation;
import java.util.List;
import java.math.BigDecimal;
import java.time.Instant;

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
class RecommendationPostgresTest {
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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void publicReadKeepsEligibilityAndProjectionInOneSnapshotDuringVisibilityChange() throws Exception {
        jdbc.update("INSERT INTO user_account(id,display_name) VALUES ('rec-rr-owner','Owner')");
        Long namespace = jdbc.queryForObject("SELECT id FROM namespace WHERE slug='global'", Long.class);
        Long skill = jdbc.queryForObject("INSERT INTO skill(namespace_id,slug,display_name,owner_id) VALUES (?, 'rec-rr','Public name','rec-rr-owner') RETURNING id", Long.class, namespace);
        Long version = jdbc.queryForObject("INSERT INTO skill_version(skill_id,version,status,download_ready) VALUES (?, '1.0.0','PUBLISHED',TRUE) RETURNING id", Long.class, skill);
        jdbc.update("UPDATE skill SET latest_version_id=? WHERE id=?", version, skill);
        var transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            var row = settings.findBySettingKey(RecommendationService.KEY).orElseThrow();
            row.update(Map.of("published", Map.of("topics", List.of(), "featuredSkillIds", List.of(skill))), "test");
            settings.saveAndFlush(row);
        });
        var snapshotRead = new CountDownLatch(1);
        var writerDone = new CountDownLatch(1);
        var summaries = mock(SkillSearchAppService.class);
        var labels = mock(SkillLabelProjectionService.class);
        // An empty candidate page must not call the barrier a second time.
        when(summaries.mapVisibleSkillSummaries(eq(List.of()), isNull())).thenReturn(List.of());
        when(summaries.mapVisibleSkillSummaries(eq(List.of(skill)), isNull())).thenAnswer(invocation -> {
            snapshotRead.countDown();
            assertThat(writerDone.await(10, TimeUnit.SECONDS)).isTrue();
            String name = jdbc.queryForObject("SELECT display_name FROM skill WHERE id=?", String.class, skill);
            return List.of(new SkillSummaryResponse(skill, "rec-rr", name, "Summary", "PUBLIC", "ACTIVE",
                    0L, 0, BigDecimal.ZERO, 0, "global", Instant.now(), false, null, null, null, "PUBLISHED", null));
        });
        try (var validators = Validation.buildDefaultValidatorFactory(); var executor = Executors.newSingleThreadExecutor()) {
            var target = new RecommendationService(settings, new ObjectMapper(), validators.getValidator(),
                    mock(SitePresentationAssetService.class), new RecommendationSkillQuery(entityManager), summaries,
                    mock(AuditLogService.class), mock(RequestIdAccessor.class), mock(RecommendationDefaults.class), labels);
            var factory = new ProxyFactory(target);
            factory.setProxyTargetClass(true);
            factory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
            var service = (RecommendationService) factory.getProxy();
            var read = executor.submit(service::published);
            assertThat(snapshotRead.await(10, TimeUnit.SECONDS)).isTrue();
            jdbc.update("UPDATE skill SET visibility='PRIVATE', display_name='PRIVATE_SECRET' WHERE id=?", skill);
            writerDone.countDown();
            assertThat(read.get(10, TimeUnit.SECONDS).featuredSkills()).extracting(SkillSummaryResponse::displayName)
                    .containsExactly("Public name");
            assertThat(service.published().featuredSkills()).isEmpty();
        } finally { writerDone.countDown(); }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void numericIdsDoNotLeaveJsonDirtyOrInvalidateTheReturnedVersion() {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            var row = settings.findBySettingKey(RecommendationService.KEY).orElseThrow();
            row.update(Map.of(), "test"); settings.saveAndFlush(row);
        });
        long version = jdbc.queryForObject("SELECT version FROM system_setting WHERE setting_key='site.recommendations'", Long.class);
        var query = mock(RecommendationSkillQuery.class);
        when(query.eligible(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        var summaries = mock(SkillSearchAppService.class);
        var defaults = mock(RecommendationDefaults.class);
        when(defaults.content()).thenReturn(RecommendationContent.empty());
        try (var validators = Validation.buildDefaultValidatorFactory()) {
            var target = new RecommendationService(settings, new ObjectMapper(), validators.getValidator(),
                    mock(SitePresentationAssetService.class), query, summaries, mock(AuditLogService.class),
                    mock(RequestIdAccessor.class), defaults, mock(SkillLabelProjectionService.class));
            var factory = new ProxyFactory(target); factory.setProxyTargetClass(true);
            factory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
            var service = (RecommendationService) factory.getProxy();
            var content = new RecommendationContent(List.of(new RecommendationTopic("numeric", "Numeric IDs", "", null, "research", true, List.of(1L))), List.of(1L));
            var saved = service.save(new RecommendationUpdateRequest(version, content), "test", new AuditRequestContext(null, null));
            assertThat(saved.version()).isEqualTo(jdbc.queryForObject("SELECT version FROM system_setting WHERE setting_key='site.recommendations'", Long.class));
            var published = service.publish(saved.version(), "test", new AuditRequestContext(null, null));
            assertThat(published.version()).isEqualTo(jdbc.queryForObject("SELECT version FROM system_setting WHERE setting_key='site.recommendations'", Long.class));
            assertThat(service.current().published()).isEqualTo(content);
            assertThat(jdbc.queryForObject("SELECT value_json #>> '{published,topics,0,coverStyle}' FROM system_setting WHERE setting_key='site.recommendations'", String.class)).isEqualTo("research");
        }
    }

    @Test
    void candidateLabelFilterKeepsEligibilityPagingAndTotalAligned() {
        jdbc.update("INSERT INTO user_account(id,display_name) VALUES ('rec-label-owner','Owner')");
        Long namespace = jdbc.queryForObject("SELECT id FROM namespace WHERE slug='global'", Long.class);
        Long label = jdbc.queryForObject("INSERT INTO label_definition(slug,type) VALUES ('rec-candidate-label','RECOMMENDED') RETURNING id", Long.class);
        var ids = new java.util.ArrayList<Long>();
        for (int i = 0; i < 4; i++) {
            Long skill = jdbc.queryForObject("INSERT INTO skill(namespace_id,slug,display_name,owner_id,visibility) VALUES (?, ?, ?, 'rec-label-owner', ?) RETURNING id", Long.class,
                    namespace, "rec-filter-" + i, "rec-filter-" + i, i == 3 ? "PRIVATE" : "PUBLIC");
            Long version = jdbc.queryForObject("INSERT INTO skill_version(skill_id,version,status,download_ready) VALUES (?, '1.0.0','PUBLISHED',TRUE) RETURNING id", Long.class, skill);
            jdbc.update("UPDATE skill SET latest_version_id=? WHERE id=?", version, skill);
            if (i != 2) jdbc.update("INSERT INTO skill_label(skill_id,label_id) VALUES (?,?)", skill, label);
            ids.add(skill);
        }
        var query = new RecommendationSkillQuery(entityManager);
        assertThat(query.count("rec-filter-", "rec-candidate-label")).isEqualTo(2);
        assertThat(query.candidates("rec-filter-", 0, 1, "rec-candidate-label")).containsExactly(ids.get(1));
        assertThat(query.candidates("rec-filter-", 1, 1, "rec-candidate-label")).containsExactly(ids.get(0));
        assertThat(query.candidates("rec-filter-", 2, 1, "rec-candidate-label")).isEmpty();
        assertThat(query.count("rec-filter-", "")).isEqualTo(3);
        assertThat(query.count("rec-filter-", "missing-label")).isZero();
        assertThat(query.candidates("rec-filter-", 0, 20, "missing-label")).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void upgradePreservesSiteSettingsInitializesOnlyUnlabeledBuiltinsAndNeverRestoresDeletion() throws Exception {
        String schema = "recommendation_upgrade_" + System.nanoTime();
        var previous = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("122").load();
        previous.migrate();
        try (var connection = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()); var statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            statement.executeUpdate("UPDATE system_setting SET value_json='{\"siteName\":\"Existing brand\"}'::jsonb WHERE setting_key='site.presentation'");
            statement.executeUpdate("INSERT INTO user_account(id,display_name) VALUES ('builtin-skill-publisher','Builtin'),('other-owner','Other')");
            statement.executeUpdate("INSERT INTO label_definition(slug,type) VALUES ('manual-label','RECOMMENDED')");
            statement.executeUpdate("INSERT INTO skill(namespace_id,slug,display_name,owner_id) SELECT id,'meeting-note-summarizer','Meeting','builtin-skill-publisher' FROM namespace WHERE slug='global'");
            statement.executeUpdate("INSERT INTO skill(namespace_id,slug,display_name,owner_id) SELECT id,'frontend-design','Design','builtin-skill-publisher' FROM namespace WHERE slug='global'");
            statement.executeUpdate("INSERT INTO skill(namespace_id,slug,display_name,owner_id) SELECT id,'meeting-note-summarizer','Other','other-owner' FROM namespace WHERE slug='global'");
            statement.executeUpdate("INSERT INTO skill_label(skill_id,label_id) SELECT s.id,l.id FROM skill s,label_definition l WHERE s.slug='frontend-design' AND l.slug='manual-label'");
            var upgrade = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("123").load();
            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
            try (var rows = statement.executeQuery("SELECT value_json->>'siteName' FROM system_setting WHERE setting_key='site.presentation'")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("Existing brand");
            }
            try (var rows = statement.executeQuery("SELECT s.display_name,l.slug FROM skill_label sl JOIN skill s ON s.id=sl.skill_id JOIN label_definition l ON l.id=sl.label_id ORDER BY s.display_name")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("Design"); assertThat(rows.getString(2)).isEqualTo("manual-label");
                assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("Meeting"); assertThat(rows.getString(2)).isEqualTo("productivity");
                assertThat(rows.next()).isFalse();
            }
            statement.executeUpdate("DELETE FROM skill_label WHERE label_id IN (SELECT id FROM label_definition WHERE slug='productivity')");
            assertThat(upgrade.migrate().migrationsExecuted).isZero();
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM skill_label sl JOIN label_definition l ON l.id=sl.label_id WHERE l.slug='productivity'")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isZero();
            }
        }
    }
}
