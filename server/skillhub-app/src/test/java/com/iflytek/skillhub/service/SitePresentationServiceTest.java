package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.settings.SystemSetting;
import com.iflytek.skillhub.auth.settings.SystemSettingRepository;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.dto.SitePresentationSettings;
import com.iflytek.skillhub.dto.SitePresentationSettings.HomepageMode;
import com.iflytek.skillhub.dto.SitePresentationUpdateRequest;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SitePresentationServiceTest {
    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    @Mock private SystemSettingRepository repository;
    @Mock private SitePresentationAssetService assets;
    @Mock private AuditLogService audit;
    @Mock private RequestIdAccessor requestIds;
    private SitePresentationService service;
    private SystemSetting stored;

    @BeforeEach
    void setup() {
        service = new SitePresentationService(repository, assets, audit, requestIds, VALIDATORS.getValidator());
        stored = new SystemSetting(SitePresentationService.SETTING_KEY, Map.of());
    }

    @AfterAll
    static void closeValidators() { VALIDATORS.close(); }

    @Test
    void missingRowReturnsDefaultsWithoutWriting() {
        when(repository.findBySettingKey(SitePresentationService.SETTING_KEY)).thenReturn(Optional.empty());
        var response = service.current();
        assertThat(response.settings()).isEqualTo(SitePresentationSettings.defaults());
        assertThat(response.version()).isZero();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void readsOnlyWhitelistedPresentationFieldsAndFillsMissingDefaults() {
        stored = new SystemSetting(SitePresentationService.SETTING_KEY,
                Map.of("siteName", "Internal Skills", "showLatest", false,
                        "heroTitle", Map.of("zh", "团队技能", "secret", "do-not-return"),
                        "smtpPassword", "do-not-return", "homepageMode", "future-layout"));
        when(repository.findBySettingKey(SitePresentationService.SETTING_KEY)).thenReturn(Optional.of(stored));
        var settings = service.current().settings();
        assertThat(settings.siteName()).isEqualTo("Internal Skills");
        assertThat(settings.showLatest()).isFalse();
        assertThat(settings.showPopular()).isTrue();
        assertThat(settings.homepageMode()).isEqualTo(HomepageMode.LANDING);
        assertThat(settings.heroTitle()).containsExactly(Map.entry("zh", "团队技能"));
        assertThat(settings.toString()).doesNotContain("do-not-return");
    }

    @Test
    void savesAllFieldsAsOneAuditedVersionedUpdate() {
        when(repository.findBySettingKey(SitePresentationService.SETTING_KEY)).thenReturn(Optional.of(stored));
        var settings = new SitePresentationSettings("  Team Skills  ", null, null, HomepageMode.CATALOG,
                Map.of("zh", " 团队技能 ", "en", "  "), Map.of("ko", "설명", "ru", "Описание"),
                false, true, false, false);
        var response = service.update(new SitePresentationUpdateRequest(settings, 0L), "admin",
                new AuditRequestContext("127.0.0.1", "browser"));
        assertThat(response.settings().siteName()).isEqualTo("Team Skills");
        assertThat(response.settings().heroTitle()).containsExactly(Map.entry("zh", "团队技能"));
        assertThat(response.settings().heroDescription()).containsKeys("ko", "ru");
        assertThat(response.settings().showPopular()).isFalse();
        assertThat(response.settings().footerVisible()).isFalse();
        assertThat(stored.getUpdatedBy()).isEqualTo("admin");
        assertThat(stored.getValue()).doesNotContainKeys("logoUrl", "faviconUrl");
        verify(repository).saveAndFlush(stored);
        verify(audit).record(eq("admin"), eq("SITE_PRESENTATION_UPDATE"), eq("SYSTEM_SETTING"),
                any(), any(), eq("127.0.0.1"), eq("browser"), any());
    }

    @Test
    void staleVersionDoesNotMutateOrAudit() {
        when(repository.findBySettingKey(SitePresentationService.SETTING_KEY)).thenReturn(Optional.of(stored));
        assertThatThrownBy(() -> service.update(new SitePresentationUpdateRequest(SitePresentationSettings.defaults(), 1L),
                "admin", new AuditRequestContext(null, null))).isInstanceOf(DomainConflictException.class);
        assertThat(stored.getValue()).isEmpty();
        verify(repository, never()).saveAndFlush(any());
        verify(audit, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"unsupported", "default"})
    void rejectsUnregisteredLocaleBeforePersistence(String locale) {
        var settings = new SitePresentationSettings(null, null, null, HomepageMode.LANDING,
                Map.of(locale, "Title"), Map.of(), true, true, true, true);
        assertThatThrownBy(() -> service.update(new SitePresentationUpdateRequest(settings, 0L), "admin",
                new AuditRequestContext(null, null))).isInstanceOf(DomainBadRequestException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsTooLongTitleAndIncompleteBooleanSettings() {
        var invalid = new SitePresentationSettings(null, null, null, HomepageMode.LANDING,
                Map.of("en", "x".repeat(161)), Map.of(), null, true, true, true);
        assertThatThrownBy(() -> service.update(new SitePresentationUpdateRequest(invalid, 0L), "admin",
                new AuditRequestContext(null, null))).isInstanceOf(DomainBadRequestException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void invalidAssetDoesNotChangePersistentSettings() {
        when(repository.findBySettingKey(SitePresentationService.SETTING_KEY)).thenReturn(Optional.of(stored));
        doThrow(new DomainBadRequestException("error.site.presentation.invalidAsset"))
                .when(assets).validateReference("https://example.com/logo.svg");
        var settings = new SitePresentationSettings(null, "https://example.com/logo.svg", null,
                HomepageMode.LANDING, Map.of(), Map.of(), true, true, true, true);
        assertThatThrownBy(() -> service.update(new SitePresentationUpdateRequest(settings, 0L), "admin",
                new AuditRequestContext(null, null))).isInstanceOf(DomainBadRequestException.class);
        assertThat(stored.getValue()).isEmpty();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void plainTextQuotesAndNewlinesRoundTripWithoutScriptSerialization() {
        when(repository.findBySettingKey(SitePresentationService.SETTING_KEY)).thenReturn(Optional.of(stored));
        String description = "First line \"quoted\"\n<script>alert(1)</script>";
        var settings = new SitePresentationSettings(null, null, null, HomepageMode.LANDING,
                Map.of(), Map.of("en", description), true, true, true, true);
        var result = service.update(new SitePresentationUpdateRequest(settings, 0L), "admin",
                new AuditRequestContext(null, null));
        assertThat(result.settings().heroDescription()).containsEntry("en", description);
    }

    @Test
    void clearingCustomValuesRestoresDefaults() {
        stored = new SystemSetting(SitePresentationService.SETTING_KEY,
                Map.of("siteName", "old", "homepageMode", "CATALOG", "footerVisible", false));
        when(repository.findBySettingKey(SitePresentationService.SETTING_KEY)).thenReturn(Optional.of(stored));
        var response = service.update(new SitePresentationUpdateRequest(SitePresentationSettings.defaults(), 0L),
                "admin", new AuditRequestContext(null, null));
        assertThat(response.settings()).isEqualTo(SitePresentationSettings.defaults());
    }

    @Test
    void persistenceFailureIsNotReportedAsSuccessOrAudited() {
        when(repository.findBySettingKey(SitePresentationService.SETTING_KEY)).thenReturn(Optional.of(stored));
        when(repository.saveAndFlush(stored)).thenThrow(new IllegalStateException("Database unavailable"));
        assertThatThrownBy(() -> service.update(new SitePresentationUpdateRequest(SitePresentationSettings.defaults(), 0L),
                "admin", new AuditRequestContext(null, null))).isInstanceOf(IllegalStateException.class);
        verify(audit, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void databaseReadFailureDoesNotBecomeDefaultSuccess() {
        when(repository.findBySettingKey(SitePresentationService.SETTING_KEY)).thenThrow(new IllegalStateException("Offline"));
        assertThatThrownBy(service::current).isInstanceOf(IllegalStateException.class);
    }
}
