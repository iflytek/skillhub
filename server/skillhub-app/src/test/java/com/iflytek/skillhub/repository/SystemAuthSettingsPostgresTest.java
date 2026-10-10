package com.iflytek.skillhub.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.auth.repository.RoleRepository;
import com.iflytek.skillhub.auth.settings.ExternalRoleGrantRule;
import com.iflytek.skillhub.auth.settings.ExternalRoleGrantRuleRepository;
import com.iflytek.skillhub.auth.settings.SystemSetting;
import com.iflytek.skillhub.auth.settings.SystemSettingRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
}
