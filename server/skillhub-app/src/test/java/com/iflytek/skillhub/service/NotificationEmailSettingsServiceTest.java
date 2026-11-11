package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.iflytek.skillhub.auth.settings.*;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.notification.email.delivery.NotificationEmailCancellation;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;

class NotificationEmailSettingsServiceTest {
    @Test
    void missingDeploymentPropertySeedsSixtyMinutesForNewDatabase() {
        var repository = mock(SystemSettingRepository.class);
        var em = mock(EntityManager.class);
        when(em.unwrap(org.hibernate.Session.class)).thenReturn(mock(org.hibernate.Session.class));
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withBean(SystemSettingRepository.class, () -> repository)
                .withBean(EntityManager.class, () -> em)
                .withBean(AuditLogService.class, () -> mock(AuditLogService.class))
                .withBean(NotificationEmailCancellation.class, () -> mock(NotificationEmailCancellation.class))
                .withBean(java.time.Clock.class, java.time.Clock::systemUTC)
                .withBean(NotificationEmailSettingsService.class)
                .run(context -> {
                    context.getBean(NotificationEmailSettingsService.class).run(null);
                    var setting = org.mockito.ArgumentCaptor.forClass(SystemSetting.class);
                    verify(repository).saveAndFlush(setting.capture());
                    assertThat(setting.getValue().getValue())
                            .containsEntry("digestWindowMinutes", 60)
                            .containsEntry("enabled", false);
                });
    }
    @Test
    void persistedSettingsIgnoreChangedInvalidSeed() {
        var repository = mock(SystemSettingRepository.class);
        var em = mock(EntityManager.class);
        when(repository.findBySettingKey("notification.email"))
                .thenReturn(Optional.of(new SystemSetting("notification.email",
                        Map.of("enabled", false, "digestWindowMinutes", 15, "publicBaseUrl", ""))));
        var service = new NotificationEmailSettingsService(repository, em, true, -1, "invalid", "", "",
                mock(AuditLogService.class), mock(NotificationEmailCancellation.class), java.time.Clock.systemUTC());
        assertThatCode(() -> service.run(null)).doesNotThrowAnyException();
        verifyNoInteractions(em);
    }
    @Test
    void enablingRequiresConfiguredTransportAndTrustedUrl() {
        var repository = mock(SystemSettingRepository.class);
        var em = mock(EntityManager.class);
        var service = new NotificationEmailSettingsService(repository, em, false, 15, "", "smtp.test",
                "noreply@test.com", mock(AuditLogService.class), mock(NotificationEmailCancellation.class), java.time.Clock.systemUTC());
        for (String url : List.of("https://good.test/#bad", "https://user:password@good.test", "javascript:alert(1)",
                     "https://good.test?x=1"))
            assertThatThrownBy(() -> service.update(true, 15, url, 0, "admin"))
                    .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class);
        assertThat(service.smtpConfigured()).isTrue();
        verifyNoInteractions(em);
    }
}
