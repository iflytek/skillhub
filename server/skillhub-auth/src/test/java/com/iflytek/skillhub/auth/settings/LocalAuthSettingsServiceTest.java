package com.iflytek.skillhub.auth.settings;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.exception.AuthFlowException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LocalAuthSettingsServiceTest {
    @Mock private SystemSettingRepository repository;

    @Test
    void separateServiceInstancesObserveCurrentStoredValue() {
        SystemSetting initial = new SystemSetting("auth.local",
                Map.of("passwordLoginEnabled", true, "selfRegistrationEnabled", true));
        ReflectionTestUtils.setField(initial, "id", 1L);
        AtomicReference<SystemSetting> stored = new AtomicReference<>(initial);
        when(repository.findBySettingKey("auth.local")).thenAnswer(invocation -> Optional.of(stored.get()));
        LocalAuthSettingsService firstInstance = new LocalAuthSettingsService(repository);
        LocalAuthSettingsService secondInstance = new LocalAuthSettingsService(repository);

        assertThat(firstInstance.current().passwordLoginEnabled()).isTrue();
        SystemSetting updated = new SystemSetting("auth.local",
                Map.of("passwordLoginEnabled", false, "selfRegistrationEnabled", true));
        ReflectionTestUtils.setField(updated, "id", 1L);
        stored.set(updated);

        assertThat(secondInstance.current().passwordLoginEnabled()).isFalse();
        assertThat(firstInstance.current().passwordLoginEnabled()).isFalse();
    }

    @Test
    void closedPasswordLoginBlocksPasswordAndRegistration() {
        SystemSetting setting = new SystemSetting("auth.local",
                Map.of("passwordLoginEnabled", false, "selfRegistrationEnabled", true));
        ReflectionTestUtils.setField(setting, "id", 1L);
        when(repository.findBySettingKey("auth.local")).thenReturn(Optional.of(setting));
        LocalAuthSettingsService service = new LocalAuthSettingsService(repository);

        assertThatThrownBy(service::requirePasswordLogin).isInstanceOf(AuthFlowException.class);
        assertThatThrownBy(service::requireSelfRegistration).isInstanceOf(AuthFlowException.class);
    }

    @Test
    void closedRegistrationLeavesPasswordLoginAvailable() {
        SystemSetting setting = new SystemSetting("auth.local",
                Map.of("passwordLoginEnabled", true, "selfRegistrationEnabled", false));
        ReflectionTestUtils.setField(setting, "id", 1L);
        when(repository.findBySettingKey("auth.local")).thenReturn(Optional.of(setting));
        LocalAuthSettingsService service = new LocalAuthSettingsService(repository);

        assertThatCode(service::requirePasswordLogin).doesNotThrowAnyException();
        assertThatThrownBy(service::requireSelfRegistration).isInstanceOf(AuthFlowException.class);
    }

    @Test
    void bothEnabledAllowBothFlows() {
        SystemSetting setting = new SystemSetting("auth.local",
                Map.of("passwordLoginEnabled", true, "selfRegistrationEnabled", true));
        ReflectionTestUtils.setField(setting, "id", 1L);
        when(repository.findBySettingKey("auth.local")).thenReturn(Optional.of(setting));
        LocalAuthSettingsService service = new LocalAuthSettingsService(repository);

        assertThatCode(service::requirePasswordLogin).doesNotThrowAnyException();
        assertThatCode(service::requireSelfRegistration).doesNotThrowAnyException();
    }

    @Test
    void bothDisabledBlockBothFlows() {
        SystemSetting setting = new SystemSetting("auth.local",
                Map.of("passwordLoginEnabled", false, "selfRegistrationEnabled", false));
        ReflectionTestUtils.setField(setting, "id", 1L);
        when(repository.findBySettingKey("auth.local")).thenReturn(Optional.of(setting));
        LocalAuthSettingsService service = new LocalAuthSettingsService(repository);

        assertThatThrownBy(service::requirePasswordLogin).isInstanceOf(AuthFlowException.class);
        assertThatThrownBy(service::requireSelfRegistration).isInstanceOf(AuthFlowException.class);
    }

    @Test
    void missingSettingFailsClosed() {
        when(repository.findBySettingKey("auth.local")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new LocalAuthSettingsService(repository).requirePasswordLogin())
                .isInstanceOf(AuthFlowException.class)
                .extracting("status").isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void databaseFailureFailsClosedWithServiceUnavailable() {
        when(repository.findBySettingKey("auth.local"))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThatThrownBy(() -> new LocalAuthSettingsService(repository).requirePasswordLogin())
                .isInstanceOf(AuthFlowException.class)
                .extracting("status").isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
