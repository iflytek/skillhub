package com.iflytek.skillhub.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.bootstrap.InitialAuthSettingsInitializer;
import com.iflytek.skillhub.bootstrap.InitialAuthSettingsProperties;
import com.iflytek.skillhub.auth.repository.RoleRepository;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.auth.identity.IdentityBindingService;
import com.iflytek.skillhub.auth.local.LocalCredential;
import com.iflytek.skillhub.auth.local.LocalCredentialRepository;
import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.settings.ExternalRoleGrantRule;
import com.iflytek.skillhub.auth.settings.ExternalRoleGrantRuleRepository;
import com.iflytek.skillhub.auth.settings.InitialExternalRoleGrantService;
import com.iflytek.skillhub.auth.settings.SystemSetting;
import com.iflytek.skillhub.auth.settings.SystemSettingRepository;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.GlobalNamespaceMembershipService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import com.iflytek.skillhub.infra.jpa.AuditLogJpaRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class SystemAuthSettingsPostgresTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired private SystemSettingRepository settings;
    @Autowired private ExternalRoleGrantRuleRepository rules;
    @Autowired private RoleRepository roles;
    @Autowired private UserAccountRepository users;
    @Autowired private IdentityBindingRepository identities;
    @Autowired private UserRoleBindingRepository userRoles;
    @Autowired private LocalCredentialRepository localCredentials;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private AuditLogJpaRepository auditLogs;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void flywaySchemaStoresSettingsAsJsonObjectWithOptimisticVersion() {
        String key = "test.auth." + UUID.randomUUID();
        SystemSetting setting = settings.saveAndFlush(new SystemSetting(key,
                Map.of("passwordLoginEnabled", true, "selfRegistrationEnabled", true)));
        Long id = setting.getId();
        entityManager.clear();

        SystemSetting reloaded = settings.findBySettingKey(key).orElseThrow();
        assertThat(reloaded.getValue().get("passwordLoginEnabled")).isEqualTo(true);
        assertThat(jdbc.queryForObject("SELECT jsonb_typeof(value_json) FROM system_setting WHERE id = ?",
                String.class, id)).isEqualTo("object");

        reloaded.update(Map.of("passwordLoginEnabled", false, "selfRegistrationEnabled", true), "admin");
        settings.saveAndFlush(reloaded);
        entityManager.clear();
        assertThat(settings.findBySettingKey(key).orElseThrow().getVersion()).isEqualTo(1L);
        assertThat(settings.findBySettingKey(key).orElseThrow().getValue().get("passwordLoginEnabled"))
                .isEqualTo(false);
    }

    @Test
    void consumedRuleKeepsActualExternalSubjectAndUser() {
        String suffix = UUID.randomUUID().toString();
        String userId = "usr_" + suffix;
        users.save(new UserAccount(userId, "new-user", null, null));
        entityManager.flush();
        var role = roles.findByCode("SUPER_ADMIN").orElseThrow();
        ExternalRoleGrantRule rule = rules.saveAndFlush(new ExternalRoleGrantRule(
                "github", suffix + "@example.com", role, "admin"));

        rule.consume("external-" + suffix, userId);
        rules.saveAndFlush(rule);
        entityManager.clear();

        ExternalRoleGrantRule reloaded = rules.findById(rule.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ExternalRoleGrantRule.Status.CONSUMED);
        assertThat(reloaded.getMatchedSubject()).isEqualTo("external-" + suffix);
        assertThat(reloaded.getGrantedUserId()).isEqualTo(userId);
        assertThat(reloaded.getGrantedAt()).isNotNull();
    }

    @Test
    void authSettingsBusinessRulesHaveNoSqlCheckConstraints() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM pg_constraint "
                + "WHERE contype = 'c' AND conrelid IN "
                + "('system_setting'::regclass, 'external_role_grant_rule'::regclass)", Integer.class);
        assertThat(count).isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void sameEmailLocalAccountStaysSeparateFromNewExternalGrant() {
        String suffix = UUID.randomUUID().toString();
        String email = suffix + "@example.com";
        String localUserId = "local_" + suffix;
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        Long ruleId = transactions.execute(status -> {
            users.save(new UserAccount(localUserId, "local", email, null));
            localCredentials.save(new LocalCredential(localUserId, "local-" + suffix, "test-hash"));
            return rules.save(new ExternalRoleGrantRule(
                    "github", email, roles.findByCode("SUPER_ADMIN").orElseThrow(), "admin")).getId();
        });

        PlatformPrincipal external = bindingService().bindOrCreate(
                new OAuthClaims("github", "external-" + suffix, email, true, "external", Map.of()),
                UserStatus.ACTIVE);

        assertThat(external.userId()).isNotEqualTo(localUserId);
        assertThat(external.platformRoles()).contains("SUPER_ADMIN");
        assertThat(users.findById(localUserId)).isPresent();
        assertThat(localCredentials.findByUserId(localUserId)).isPresent();
        assertThat(userRoles.findByUserId(localUserId)).isEmpty();
        assertThat(userRoles.findByUserId(external.userId())).hasSize(1);
        assertThat(identities.findByProviderCodeAndSubject("github", "external-" + suffix)
                .orElseThrow().getUserId()).isEqualTo(external.userId());
        assertThat(rules.findById(ruleId).orElseThrow().getGrantedUserId()).isEqualTo(external.userId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void deletedInitialRuleDoesNotReturnAfterInitializerRunsAgain() {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        transactions.executeWithoutResult(status -> {
            jdbc.execute("TRUNCATE TABLE user_account, external_role_grant_rule CASCADE");
            jdbc.update("DELETE FROM system_setting WHERE setting_key IN (?, ?)",
                    "auth.local", "auth.initial-role-grants.initialized");
        });
        InitialAuthSettingsProperties properties = new InitialAuthSettingsProperties();
        properties.setRoleGrantsJson("[{\"provider\":\"github\",\"email\":\"admin@example.com\",\"role\":\"SUPER_ADMIN\"}]");
        InitialAuthSettingsInitializer initializer = new InitialAuthSettingsInitializer(
                properties, settings, rules, roles, new ObjectMapper(), jdbc);
        ApplicationArguments args = mock(ApplicationArguments.class);

        transactions.executeWithoutResult(status -> initializer.run(args));
        Long ruleId = transactions.execute(status -> {
            assertThat(rules.findAll()).hasSize(1);
            assertThat(settings.findBySettingKey("auth.initial-role-grants.initialized")).isPresent();
            return rules.findAll().getFirst().getId();
        });

        transactions.executeWithoutResult(status -> rules.deleteById(ruleId));
        InitialAuthSettingsInitializer restarted = new InitialAuthSettingsInitializer(
                properties, settings, rules, roles, new ObjectMapper(), jdbc);
        transactions.executeWithoutResult(status -> restarted.run(args));

        transactions.executeWithoutResult(status -> {
            assertThat(rules.findAll()).isEmpty();
            assertThat(settings.findBySettingKey("auth.initial-role-grants.initialized")).isPresent();
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentFirstLoginConsumesRuleOnceAndDoesNotRestoreManuallyRemovedRole() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String email = suffix + "@example.com";
        String subject = "external-" + suffix;
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        Long ruleId = transactions.execute(status -> rules.save(new ExternalRoleGrantRule(
                "github", email, roles.findByCode("SUPER_ADMIN").orElseThrow(), "admin")).getId());
        IdentityBindingService bindingService = bindingService();
        OAuthClaims claims = new OAuthClaims("github", subject, email, true, "admin", Map.of());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> firstLogin(bindingService, claims, ready, start));
            var second = executor.submit(() -> firstLogin(bindingService, claims, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            PlatformPrincipal firstPrincipal = first.get(20, TimeUnit.SECONDS);
            PlatformPrincipal secondPrincipal = second.get(20, TimeUnit.SECONDS);
            assertThat(firstPrincipal.userId()).isEqualTo(secondPrincipal.userId());
            assertThat(firstPrincipal.platformRoles()).contains("SUPER_ADMIN");
            assertThat(secondPrincipal.platformRoles()).contains("SUPER_ADMIN");

            ExternalRoleGrantRule consumed = rules.findById(ruleId).orElseThrow();
            assertThat(consumed.getStatus()).isEqualTo(ExternalRoleGrantRule.Status.CONSUMED);
            assertThat(consumed.getGrantedUserId()).isEqualTo(firstPrincipal.userId());
            assertThat(identities.findByProviderCodeAndSubject("github", subject)).isPresent();
            assertThat(userRoles.findByUserId(firstPrincipal.userId())).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM identity_binding WHERE provider_code = ? AND subject = ?",
                    Long.class, "github", subject)).isEqualTo(1L);

            OAuthClaims anotherSubject = new OAuthClaims("github", "other-" + suffix, email, true,
                    "another-admin", Map.of());
            PlatformPrincipal independent = bindingService.bindOrCreate(anotherSubject, UserStatus.ACTIVE);
            assertThat(independent.userId()).isNotEqualTo(firstPrincipal.userId());
            assertThat(independent.platformRoles()).doesNotContain("SUPER_ADMIN");
            assertThat(userRoles.findByUserId(independent.userId())).isEmpty();

            transactions.executeWithoutResult(status -> userRoles.deleteByUserId(firstPrincipal.userId()));
            PlatformPrincipal returning = bindingService.bindOrCreate(claims, UserStatus.ACTIVE);
            assertThat(returning.platformRoles()).doesNotContain("SUPER_ADMIN");
            assertThat(userRoles.findByUserId(firstPrincipal.userId())).isEmpty();
            assertThat(rules.findById(ruleId).orElseThrow().getGrantedUserId()).isEqualTo(firstPrincipal.userId());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentDifferentSubjectsSharingEmailCannotBothConsumeRule() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String email = suffix + "@example.com";
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        Long ruleId = transactions.execute(status -> rules.save(new ExternalRoleGrantRule(
                "github", email, roles.findByCode("SUPER_ADMIN").orElseThrow(), "admin")).getId());
        IdentityBindingService bindingService = bindingService();
        OAuthClaims firstClaims = new OAuthClaims("github", "first-" + suffix, email, true, "first", Map.of());
        OAuthClaims secondClaims = new OAuthClaims("github", "second-" + suffix, email, true, "second", Map.of());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> firstLogin(bindingService, firstClaims, ready, start));
            var second = executor.submit(() -> firstLogin(bindingService, secondClaims, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            PlatformPrincipal firstPrincipal = first.get(20, TimeUnit.SECONDS);
            PlatformPrincipal secondPrincipal = second.get(20, TimeUnit.SECONDS);

            assertThat(firstPrincipal.userId()).isNotEqualTo(secondPrincipal.userId());
            assertThat(firstPrincipal.platformRoles().contains("SUPER_ADMIN"))
                    .isNotEqualTo(secondPrincipal.platformRoles().contains("SUPER_ADMIN"));
            ExternalRoleGrantRule consumed = rules.findById(ruleId).orElseThrow();
            assertThat(consumed.getStatus()).isEqualTo(ExternalRoleGrantRule.Status.CONSUMED);
            assertThat(consumed.getGrantedUserId()).isIn(firstPrincipal.userId(), secondPrincipal.userId());
            assertThat(consumed.getMatchedSubject()).isIn(firstClaims.subject(), secondClaims.subject());
            assertThat(userRoles.findByUserId(firstPrincipal.userId()).size()
                    + userRoles.findByUserId(secondPrincipal.userId()).size()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = ? AND target_id = ?",
                    Long.class, "INITIAL_ROLE_GRANTED", ruleId)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("SELECT detail_json ->> 'userId' FROM audit_log "
                    + "WHERE action = ? AND target_id = ?", String.class, "INITIAL_ROLE_GRANTED", ruleId))
                    .isEqualTo(consumed.getGrantedUserId());
        }
    }

    private IdentityBindingService bindingService() {
        return new IdentityBindingService(identities, users, userRoles,
                mock(GlobalNamespaceMembershipService.class), mock(ApplicationEventPublisher.class),
                transactionManager, new InitialExternalRoleGrantService(
                        rules, userRoles, new AuditLogService(auditLogs, Clock.systemUTC())));
    }

    private static PlatformPrincipal firstLogin(IdentityBindingService service, OAuthClaims claims,
                                                CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for first login");
        return service.bindOrCreate(claims, UserStatus.ACTIVE);
    }
}
