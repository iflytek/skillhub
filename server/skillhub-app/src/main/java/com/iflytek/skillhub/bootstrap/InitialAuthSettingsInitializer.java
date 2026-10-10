package com.iflytek.skillhub.bootstrap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.repository.RoleRepository;
import com.iflytek.skillhub.auth.settings.ExternalRoleGrantRule;
import com.iflytek.skillhub.auth.settings.ExternalRoleGrantRuleRepository;
import com.iflytek.skillhub.auth.settings.InitialExternalRoleGrantService;
import com.iflytek.skillhub.auth.settings.LocalAuthSettingsService;
import com.iflytek.skillhub.auth.settings.SystemSetting;
import com.iflytek.skillhub.auth.settings.SystemSettingRepository;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import java.sql.Statement;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Seeds deployment defaults once, before other application runners can create a local admin. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InitialAuthSettingsInitializer implements ApplicationRunner {
    private static final String GRANT_SEED_MARKER = "auth.initial-role-grants.initialized";
    private static final Pattern PROVIDER = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private final InitialAuthSettingsProperties properties;
    private final SystemSettingRepository settings;
    private final ExternalRoleGrantRuleRepository rules;
    private final RoleRepository roles;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public InitialAuthSettingsInitializer(InitialAuthSettingsProperties properties,
                                          SystemSettingRepository settings,
                                          ExternalRoleGrantRuleRepository rules,
                                          RoleRepository roles,
                                          ObjectMapper objectMapper,
                                          JdbcTemplate jdbcTemplate) {
        this.properties = properties;
        this.settings = settings;
        this.rules = rules;
        this.roles = roles;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Serialize initialization across PostgreSQL replicas before checking missing rows.
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            if ("PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SELECT pg_advisory_xact_lock(92320261010)");
                }
            }
            return null;
        });
        if (settings.findBySettingKey(LocalAuthSettingsService.SETTING_KEY).isEmpty()) {
            settings.save(new SystemSetting(LocalAuthSettingsService.SETTING_KEY, Map.of(
                    LocalAuthSettingsService.PASSWORD_LOGIN_KEY, properties.isPasswordLoginEnabled(),
                    LocalAuthSettingsService.SELF_REGISTRATION_KEY, properties.isSelfRegistrationEnabled())));
        }
        if (settings.findBySettingKey(GRANT_SEED_MARKER).isPresent()) {
            return;
        }
        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_account", Long.class) == 0L) {
            seedInitialRules();
        }
        settings.save(new SystemSetting(GRANT_SEED_MARKER, Map.of("initialized", true)));
    }

    private void seedInitialRules() {
        List<SeedRule> initialRules;
        try {
            initialRules = objectMapper.readValue(properties.getRoleGrantsJson(), new TypeReference<>() {});
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid initial external role grants JSON", exception);
        }
        if (initialRules == null) {
            throw new IllegalStateException("Initial external role grants must be an array");
        }
        Set<String> identities = new HashSet<>();
        for (SeedRule rule : initialRules) {
            if (rule == null) throw new IllegalStateException("Initial role grant may not be null");
            String provider = InitialExternalRoleGrantService.normalize(rule.provider());
            String email = InitialExternalRoleGrantService.normalize(rule.email());
            if (!PROVIDER.matcher(provider).matches() || !EMAIL.matcher(email).matches()) {
                throw new IllegalStateException("Invalid initial role grant identity");
            }
            if ("feishu".equals(provider) || "dingtalk".equals(provider)) {
                throw new IllegalStateException("Provider does not attest verified email for initial role grants: " + provider);
            }
            if (!identities.add(provider + ":" + email)) {
                throw new IllegalStateException("Duplicate initial role grant identity");
            }
            Role role = roles.findByCode(rule.role())
                    .filter(Role::isSystem)
                    .orElseThrow(() -> new IllegalStateException("Invalid initial role grant role"));
            rules.save(new ExternalRoleGrantRule(provider, email, role, null));
        }
    }

    public record SeedRule(String provider, String email, String role) {}
}
