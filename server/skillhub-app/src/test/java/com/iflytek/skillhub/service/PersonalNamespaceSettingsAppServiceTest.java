package com.iflytek.skillhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.PersonalNamespaceProvisioningService;
import com.iflytek.skillhub.domain.namespace.PersonalNamespaceSettings;
import com.iflytek.skillhub.dto.PersonalNamespaceSettingsResponse;
import com.iflytek.skillhub.dto.PersonalNamespaceSettingsUpdateRequest;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PersonalNamespaceSettingsAppServiceTest {

    @Mock
    private PersonalNamespaceProvisioningService personalNamespaceProvisioningService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private RequestIdAccessor requestIdAccessor;

    private PersonalNamespaceSettingsAppService service;

    @BeforeEach
    void setUp() {
        service = new PersonalNamespaceSettingsAppService(
                personalNamespaceProvisioningService,
                auditLogService,
                requestIdAccessor,
                new ObjectMapper());
        when(requestIdAccessor.current()).thenReturn("req-1");
    }

    @Test
    void getExposesTheEffectiveSettingsAndSupportedPlaceholders() {
        when(personalNamespaceProvisioningService.currentSettings())
                .thenReturn(new PersonalNamespaceSettings(true, "${username}", "${username}"));

        PersonalNamespaceSettingsResponse response = service.get();

        assertThat(response.enabled()).isTrue();
        assertThat(response.slugTemplate()).isEqualTo("${username}");
        assertThat(response.supportedPlaceholders())
                .containsExactly("username", "email_prefix", "user_id");
    }

    @Test
    void updateTrimsTemplatesBeforeStoringThem() {
        when(personalNamespaceProvisioningService.currentSettings())
                .thenReturn(new PersonalNamespaceSettings(false, "${username}", "${username}"));

        service.update(
                new PersonalNamespaceSettingsUpdateRequest(true, "  ${username}-space  ", "  ${username}  "),
                "usr_admin",
                new AuditRequestContext("10.0.0.1", "curl/8"));

        ArgumentCaptor<PersonalNamespaceSettings> captor =
                ArgumentCaptor.forClass(PersonalNamespaceSettings.class);
        verify(personalNamespaceProvisioningService).updateSettings(captor.capture(), eq("usr_admin"));
        assertThat(captor.getValue().enabled()).isTrue();
        assertThat(captor.getValue().slugTemplate()).isEqualTo("${username}-space");
        assertThat(captor.getValue().displayNameTemplate()).isEqualTo("${username}");
    }

    @Test
    void updateRecordsAnAuditEntryWithBeforeAndAfter() {
        when(personalNamespaceProvisioningService.currentSettings())
                .thenReturn(new PersonalNamespaceSettings(false, "${username}", "${username}"));

        service.update(
                new PersonalNamespaceSettingsUpdateRequest(true, "${username}-space", "${username}"),
                "usr_admin",
                new AuditRequestContext("10.0.0.1", "curl/8"));

        ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).record(
                eq("usr_admin"),
                eq("SYSTEM_SETTING_PERSONAL_NAMESPACE_UPDATE"),
                eq("SYSTEM_SETTING"),
                isNull(),
                eq("req-1"),
                eq("10.0.0.1"),
                eq("curl/8"),
                detailCaptor.capture());
        assertThat(detailCaptor.getValue())
                .contains("\"before\"")
                .contains("\"after\"")
                .contains("${username}-space");
    }

    @Test
    void updateToleratesAMissingAuditContext() {
        when(personalNamespaceProvisioningService.currentSettings())
                .thenReturn(new PersonalNamespaceSettings(false, "${username}", "${username}"));

        service.update(
                new PersonalNamespaceSettingsUpdateRequest(false, "${username}", "${username}"),
                "usr_admin",
                null);

        verify(auditLogService).record(any(), any(), any(), isNull(), any(), isNull(), isNull(), any());
    }
}
