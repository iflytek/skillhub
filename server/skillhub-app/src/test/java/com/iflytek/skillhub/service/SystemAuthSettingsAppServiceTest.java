package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.iflytek.skillhub.auth.repository.RoleRepository;
import com.iflytek.skillhub.auth.settings.ExternalRoleGrantRule;
import com.iflytek.skillhub.auth.settings.ExternalRoleGrantRuleRepository;
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
    void duplicateActiveGrantIsRejectedBeforeWrite() {
        when(rules.existsByProviderCodeAndNormalizedEmailAndStatus(
                "feishu", "admin@example.com", ExternalRoleGrantRule.Status.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> service.createRule(
                new ExternalRoleGrantCreateRequest(" FEISHU ", "Admin@Example.Com", "SUPER_ADMIN"),
                "admin", new AuditRequestContext(null, null)))
                .isInstanceOf(DomainConflictException.class);
        verify(rules, never()).saveAndFlush(any());
    }
}
