package com.iflytek.skillhub.bootstrap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.repository.RoleRepository;
import com.iflytek.skillhub.auth.settings.ExternalRoleGrantRuleRepository;
import com.iflytek.skillhub.auth.settings.LocalAuthSettingsService;
import com.iflytek.skillhub.auth.settings.SystemSetting;
import com.iflytek.skillhub.auth.settings.SystemSettingRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class InitialAuthSettingsInitializerTest {
    @Mock private SystemSettingRepository settings;
    @Mock private ExternalRoleGrantRuleRepository rules;
    @Mock private RoleRepository roles;
    @Mock private JdbcTemplate jdbc;
    @Mock private ApplicationArguments args;

    @Test
    void emptyDatabaseGetsDefaultSettingsAndPermanentRuleSeedMarker() {
        when(settings.findBySettingKey(LocalAuthSettingsService.SETTING_KEY)).thenReturn(Optional.empty());
        when(settings.findBySettingKey("auth.initial-role-grants.initialized")).thenReturn(Optional.empty());
        when(jdbc.queryForObject("SELECT COUNT(*) FROM user_account", Long.class)).thenReturn(0L);
        InitialAuthSettingsProperties properties = new InitialAuthSettingsProperties();

        new InitialAuthSettingsInitializer(properties, settings, rules, roles, new ObjectMapper(), jdbc).run(args);

        verify(settings).save(org.mockito.ArgumentMatchers.argThat(setting ->
                setting.getSettingKey().equals(LocalAuthSettingsService.SETTING_KEY)
                        && setting.getValue().equals(Map.of("passwordLoginEnabled", true,
                                "selfRegistrationEnabled", true))));
        verify(settings).save(org.mockito.ArgumentMatchers.argThat(setting ->
                setting.getSettingKey().equals("auth.initial-role-grants.initialized")));
        verify(rules, never()).save(any());
    }

    @Test
    void existingMarkerPreventsDeletedRulesFromBeingSeededAgain() {
        when(settings.findBySettingKey(LocalAuthSettingsService.SETTING_KEY))
                .thenReturn(Optional.of(new SystemSetting(LocalAuthSettingsService.SETTING_KEY,
                        Map.of("passwordLoginEnabled", false, "selfRegistrationEnabled", true))));
        when(settings.findBySettingKey("auth.initial-role-grants.initialized"))
                .thenReturn(Optional.of(new SystemSetting("auth.initial-role-grants.initialized",
                        Map.of("initialized", true))));
        InitialAuthSettingsProperties properties = new InitialAuthSettingsProperties();
        properties.setRoleGrantsJson("[{\"provider\":\"feishu\",\"email\":\"admin@example.com\",\"role\":\"SUPER_ADMIN\"}]");

        new InitialAuthSettingsInitializer(properties, settings, rules, roles, new ObjectMapper(), jdbc).run(args);

        verify(settings, never()).save(any());
        verify(rules, never()).save(any());
        verify(jdbc, never()).queryForObject(eq("SELECT COUNT(*) FROM user_account"), eq(Long.class));
    }

    @Test
    void initialGrantForUnverifiedProviderFailsStartupInsteadOfCreatingDeadRule() {
        when(settings.findBySettingKey(LocalAuthSettingsService.SETTING_KEY)).thenReturn(Optional.empty());
        when(settings.findBySettingKey("auth.initial-role-grants.initialized")).thenReturn(Optional.empty());
        when(jdbc.queryForObject("SELECT COUNT(*) FROM user_account", Long.class)).thenReturn(0L);
        InitialAuthSettingsProperties properties = new InitialAuthSettingsProperties();
        properties.setRoleGrantsJson("[{\"provider\":\"feishu\",\"email\":\"admin@example.com\",\"role\":\"SUPER_ADMIN\"}]");

        assertThatThrownBy(() -> new InitialAuthSettingsInitializer(
                properties, settings, rules, roles, new ObjectMapper(), jdbc).run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("verified email");
        verify(rules, never()).save(any());
    }
}
