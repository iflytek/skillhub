package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.iflytek.skillhub.auth.repository.RoleRepository;
import com.iflytek.skillhub.auth.settings.ExternalRoleGrantRule;
import com.iflytek.skillhub.auth.settings.ExternalRoleGrantRuleRepository;
import com.iflytek.skillhub.auth.settings.LocalAuthSettings;
import com.iflytek.skillhub.auth.settings.LocalAuthSettingsService;
import com.iflytek.skillhub.auth.settings.SystemSetting;
import com.iflytek.skillhub.auth.settings.SystemSettingRepository;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.dto.ExternalRoleGrantCreateRequest;
import com.iflytek.skillhub.dto.SystemAuthSettingsUpdateRequest;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SystemAuthSettingsAppServiceTest {
    @Mock private LocalAuthSettingsService localSettings;
    @Mock private SystemSettingRepository settings;
    @Mock private ExternalRoleGrantRuleRepository rules;
    @Mock private RoleRepository roles;
    @Mock private AuditLogService audit;
    @Mock private RequestIdAccessor requestIds;
    private SystemAuthSettingsAppService service;

    @BeforeEach
    void setUp() {
        service = new SystemAuthSettingsAppService(localSettings, settings, rules, roles, audit, requestIds);
    }

    @Test
    void staleSettingsVersionCannotOverwriteCurrentValue() {
        SystemSetting current = new SystemSetting("auth.local",
                Map.of("passwordLoginEnabled", true, "selfRegistrationEnabled", true));
        when(settings.findBySettingKey("auth.local")).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.updateLocalSettings(
                new SystemAuthSettingsUpdateRequest(false, true, 7L), "admin",
                new AuditRequestContext(null, null)))
                .isInstanceOf(DomainConflictException.class);
        verify(settings, never()).saveAndFlush(any());
    }

    @Test
    void successfulSettingsChangeWritesAuditAndReturnsStoredValue() {
        SystemSetting current = new SystemSetting("auth.local",
                Map.of("passwordLoginEnabled", true, "selfRegistrationEnabled", true));
        ReflectionTestUtils.setField(current, "id", 1L);
        when(settings.findBySettingKey("auth.local")).thenReturn(Optional.of(current));
        when(localSettings.current()).thenReturn(new LocalAuthSettings(1L, false, true, 1L, null));

        var response = service.updateLocalSettings(
                new SystemAuthSettingsUpdateRequest(false, true, 0L), "admin",
                new AuditRequestContext("127.0.0.1", "test"));

        org.assertj.core.api.Assertions.assertThat(response.passwordLoginEnabled()).isFalse();
        org.assertj.core.api.Assertions.assertThat(current.getValue().get("passwordLoginEnabled")).isEqualTo(false);
        verify(settings).saveAndFlush(current);
        verify(audit).record(org.mockito.ArgumentMatchers.eq("admin"),
                org.mockito.ArgumentMatchers.eq("SYSTEM_AUTH_SETTINGS_UPDATE"),
                org.mockito.ArgumentMatchers.eq("SYSTEM_SETTING"),
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(null),
                org.mockito.ArgumentMatchers.eq("127.0.0.1"),
                org.mockito.ArgumentMatchers.eq("test"), any());
    }

    @Test
    void duplicateActiveGrantIsRejectedBeforeWrite() {
        when(rules.existsByProviderCodeAndNormalizedEmailAndStatus(
                "github", "admin@example.com", ExternalRoleGrantRule.Status.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> service.createRule(
                new ExternalRoleGrantCreateRequest(" GITHUB ", "Admin@Example.Com", "SUPER_ADMIN"),
                "admin", new AuditRequestContext(null, null)))
                .isInstanceOf(DomainConflictException.class);
        verify(rules, never()).saveAndFlush(any());
    }

    @Test
    void providerWithoutVerifiedEmailCannotCreateDeadGrantRule() {
        assertThatThrownBy(() -> service.createRule(
                new ExternalRoleGrantCreateRequest("feishu", "admin@example.com", "SUPER_ADMIN"),
                "admin", new AuditRequestContext(null, null)))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class);
        verify(rules, never()).saveAndFlush(any());
    }
}
