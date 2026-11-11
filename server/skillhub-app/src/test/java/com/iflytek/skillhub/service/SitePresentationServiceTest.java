package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.settings.SystemSetting;
import com.iflytek.skillhub.auth.settings.SystemSettingRepository;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.dto.SitePresentationSection;
import com.iflytek.skillhub.dto.SitePresentationSettings;
import com.iflytek.skillhub.dto.SitePresentationUpdateRequest;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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
    void missingRowReturnsAllEightUnchangedSectionsWithoutWriting() {
        when(repository.findBySettingKey(SitePresentationService.SETTING_KEY)).thenReturn(Optional.empty());
        var response = service.current();
        assertThat(response.settings()).isEqualTo(SitePresentationSettings.defaults());
        assertThat(response.settings().sections()).containsOnlyKeys(
                "hero", "capabilities", "quickStart", "popular", "latest", "enterprise", "cta", "footer");
        response.settings().sections().values().forEach(section -> {
            assertThat(section.enabled()).isTrue();
            assertThat(section.texts()).isEmpty();
            assertThat(section.items()).isEmpty();
        });
        assertThat(response.settings().links()).isEmpty();
        assertThat(response.version()).isZero();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void legacyFieldsAreProjectedWithoutWritesOrModeSelection() {
        stored = new SystemSetting(SitePresentationService.SETTING_KEY, Map.of(
                "siteName", "Internal Skills", "showPopular", false, "showLatest", false,
                "showQuickStart", false, "footerVisible", false,
                "heroTitle", Map.of("zh", "团队技能", "secret", "do-not-return"),
                "heroDescription", Map.of("en", "Description"),
                "smtpPassword", "do-not-return", "homepageMode", "CATALOG"));
        when(repository.findBySettingKey(SitePresentationService.SETTING_KEY)).thenReturn(Optional.of(stored));
        var response = service.current();
        var settings = response.settings();
        assertThat(settings.siteName()).isEqualTo("Internal Skills");
        for (String id : new String[]{"popular", "latest", "quickStart", "footer"}) {
            assertThat(settings.sections().get(id).enabled()).isFalse();
        }
        for (String id : new String[]{"hero", "capabilities", "enterprise", "cta"}) {
            assertThat(settings.sections().get(id).enabled()).isTrue();
        }
        assertThat(settings.sections().get("hero").texts().get("title")).containsExactly(Map.entry("zh", "团队技能"));
        assertThat(settings.sections().get("hero").texts().get("description")).containsEntry("en", "Description");
        assertThat(settings.toString()).doesNotContain("do-not-return", "CATALOG", "homepageMode");
        assertThat(response.version()).isEqualTo(stored.getVersion()).isZero();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void explicitNewEmptySectionsNeverResurrectLegacyValues() {
        stored = new SystemSetting(SitePresentationService.SETTING_KEY, Map.of(
                "sections", Map.of(), "heroTitle", Map.of("en", "OLD"), "showLatest", false));
        when(repository.findBySettingKey(SitePresentationService.SETTING_KEY)).thenReturn(Optional.of(stored));
        assertThat(service.current().settings()).isEqualTo(SitePresentationSettings.defaults());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void explicitNewEmptyTextsAndMissingItemsUseProductDefaults() {
        stored = new SystemSetting(SitePresentationService.SETTING_KEY, Map.of(
                "sections", Map.of("hero", Map.of("enabled", false, "texts", Map.of())),
                "heroTitle", Map.of("en", "OLD"), "footerVisible", false));
        when(repository.findBySettingKey(SitePresentationService.SETTING_KEY)).thenReturn(Optional.of(stored));
        var settings = service.current().settings();
        assertThat(settings.sections().get("hero").enabled()).isFalse();
        assertThat(settings.sections().get("hero").texts()).isEmpty();
        assertThat(settings.sections().get("hero").items()).isEmpty();
        assertThat(settings.sections().get("footer").enabled()).isTrue();
    }

    @Test
    void readFiltersUnknownSlotsItemsLocalesLinksAndSecrets() {
        stored = new SystemSetting(SitePresentationService.SETTING_KEY, Map.of(
                "sections", Map.of("hero", Map.of("enabled", true,
                        "texts", Map.of("title", Map.of("zh", "标题", "default", "secret"), "html", Map.of("en", "secret")),
                        "items", Map.of("preview", false, "arbitrary", false)),
                        "unknown", Map.of("texts", Map.of("password", "secret"))),
                "links", Map.of("documentation", "https://docs.example.test", "unknown", "secret", "repository", "javascript:secret")));
        when(repository.findBySettingKey(SitePresentationService.SETTING_KEY)).thenReturn(Optional.of(stored));
        var settings = service.current().settings();
        assertThat(settings.sections().get("hero").texts()).containsExactly(Map.entry("title", Map.of("zh", "标题")));
        assertThat(settings.sections().get("hero").items()).containsExactly(Map.entry("preview", false));
        assertThat(settings.links()).containsExactly(Map.entry("documentation", "https://docs.example.test"));
        assertThat(settings.toString()).doesNotContain("secret", "unknown", "arbitrary");
    }

    @Test
    void savesAllRegisteredSectionsAndTextSlotsAsOneAuditedUpdate() {
        when(repository.findBySettingKey(SitePresentationService.SETTING_KEY)).thenReturn(Optional.of(stored));
        Map<String, SitePresentationSection> sections = new LinkedHashMap<>();
        for (String id : SitePresentationSettings.SECTION_IDS) {
            Map<String, Map<String, String>> texts = new LinkedHashMap<>();
            for (String slot : SitePresentationSchema.TEXT_SLOTS.get(id)) texts.put(slot, Map.of("zh", "自定义 " + slot, "en", "Custom " + slot));
            Map<String, Boolean> items = new LinkedHashMap<>();
            for (String item : SitePresentationSchema.ITEM_SLOTS.get(id)) items.put(item, false);
            sections.put(id, new SitePresentationSection(false, texts, items));
        }
        assertThat(SitePresentationSchema.TEXT_SLOTS.values().stream().mapToInt(java.util.Set::size).sum()).isEqualTo(88);
        var settings = new SitePresentationSettings("  Team Skills  ", null, null, sections,
                Map.of("documentation", "https://docs.example.test/guide", "repository", "/internal/source"));
        var response = update(settings);
        assertThat(response.settings().siteName()).isEqualTo("Team Skills");
        assertThat(response.settings().sections()).isEqualTo(sections);
        assertThat(response.settings().links()).isEqualTo(settings.links());
        assertThat(stored.getUpdatedBy()).isEqualTo("admin");
        assertThat(stored.getValue()).containsOnlyKeys("siteName", "sections", "links");
        verify(repository).saveAndFlush(stored);
        var detail = ArgumentCaptor.forClass(String.class);
        verify(audit).record(eq("admin"), eq("SITE_PRESENTATION_UPDATE"), eq("SYSTEM_SETTING"),
                any(), any(), eq("127.0.0.1"), eq("browser"), detail.capture());
        assertThat(detail.getValue()).contains("previous", "current", "sections", "links");
    }

    @Test
    void saveRemovesOldShapeAndClearedOverrides() {
        stored = new SystemSetting(SitePresentationService.SETTING_KEY, Map.of(
                "heroTitle", Map.of("en", "Old"), "showLatest", false, "homepageMode", "CATALOG"));
        when(repository.findBySettingKey(SitePresentationService.SETTING_KEY)).thenReturn(Optional.of(stored));
        var settings = new SitePresentationSettings(" ", null, null,
                Map.of("hero", new SitePresentationSection(true, Map.of("title", Map.of("en", " ")), null)),
                Map.of("repository", ""));
        assertThat(update(settings).settings()).isEqualTo(SitePresentationSettings.defaults());
        assertThat(stored.getValue()).containsOnlyKeys("sections", "links");
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
        invalid(new SitePresentationSettings(null, null, null,
                Map.of("hero", new SitePresentationSection(true, Map.of("title", Map.of(locale, "Title")), Map.of())), Map.of()));
    }

    @Test
    void rejectsUnknownSectionTextItemAndLinkSlots() {
        invalid(new SitePresentationSettings(null, null, null, Map.of("custom", SitePresentationSection.defaults()), Map.of()));
        invalid(withSection("hero", new SitePresentationSection(true, Map.of("html", Map.of("en", "X")), Map.of())));
        invalid(withSection("popular", new SitePresentationSection(true, Map.of(), Map.of("query", false))));
        invalid(new SitePresentationSettings(null, null, null, SitePresentationSettings.defaults().sections(), Map.of("other", "/x")));
    }

    @Test
    void requiresSectionsAndExplicitBooleansForSubmittedSections() {
        invalid(new SitePresentationSettings(null, null, null, null, Map.of()));
        invalid(new SitePresentationSettings(null, null, null, Map.of(), Map.of()));
        invalid(withSection("hero", new SitePresentationSection(null, Map.of(), Map.of())));
        invalid(withSection("hero", new SitePresentationSection(true, null, Map.of())));
    }

    @Test
    void acceptsExactTextLimitsIncludingFooterCopyright() {
        when(repository.findBySettingKey(SitePresentationService.SETTING_KEY)).thenReturn(Optional.of(stored));
        var settings = new SitePresentationSettings("N".repeat(80), null, null, Map.of(
                "hero", new SitePresentationSection(true, Map.of("title", Map.of("en", "T".repeat(160)),
                        "description", Map.of("zh", "述".repeat(1000))), Map.of()),
                "footer", new SitePresentationSection(true, Map.of("copyright", Map.of("en", "C".repeat(1000))), Map.of())), Map.of());
        var response = update(settings).settings();
        assertThat(response.siteName()).hasSize(80);
        assertThat(response.sections().get("hero").texts().get("title").get("en")).hasSize(160);
        assertThat(response.sections().get("footer").texts().get("copyright").get("en")).hasSize(1000);
    }

    @Test
    void rejectsOverlongSiteTitleDescriptionAndLabels() {
        invalid(new SitePresentationSettings("N".repeat(81), null, null, SitePresentationSettings.defaults().sections(), Map.of()));
        invalid(withSection("hero", new SitePresentationSection(true, Map.of("title", Map.of("en", "T".repeat(161))), Map.of())));
        invalid(withSection("hero", new SitePresentationSection(true, Map.of("description", Map.of("en", "D".repeat(1001))), Map.of())));
        invalid(withSection("hero", new SitePresentationSection(true, Map.of("badge", Map.of("en", "B".repeat(161))), Map.of())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "data:text/html,x", "file:///tmp/x", "ftp://example.test", "//example.test/x",
            "/\\evil.test", "https://user:pass@example.test", "https://example.test/%0a", "https://example.test/%250d", "/%2fevil.test",
            "/%252fevil.test", "https://example.test/%5c", "https://example.test/%C2%85", "https://example.test:65536/x",
            "https://example.test:0/x", "http:example.test", "https:///missing-host", "https://example.test/\\x", "https://example.test/\n"})
    void rejectsUnsafeExternalNavigationTargets(String url) {
        invalid(new SitePresentationSettings(null, null, null, SitePresentationSettings.defaults().sections(), Map.of("repository", url)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://docs.example.test/guide?q=1#intro", "http://docs.example.test:8080/guide", "/help?return=%2Fdashboard",
            "/docs/file%20name", "/docs/100%25", "/", "HTTPS://docs.example.test/帮助"})
    void acceptsSafeExternalAndRootRelativeTargets(String url) {
        when(repository.findBySettingKey(SitePresentationService.SETTING_KEY)).thenReturn(Optional.of(stored));
        var settings = new SitePresentationSettings(null, null, null, SitePresentationSettings.defaults().sections(), Map.of("repository", url));
        assertThat(update(settings).settings().links()).containsEntry("repository", url);
    }

    @Test
    void invalidAssetDoesNotChangePersistentSettings() {
        when(repository.findBySettingKey(SitePresentationService.SETTING_KEY)).thenReturn(Optional.of(stored));
        doThrow(new DomainBadRequestException("error.site.presentation.invalidAsset")).when(assets).validateReference("https://example.com/logo.svg");
        invalid(new SitePresentationSettings(null, "https://example.com/logo.svg", null, SitePresentationSettings.defaults().sections(), Map.of()));
        assertThat(stored.getValue()).isEmpty();
    }

    @Test
    void plainTextQuotesAndNewlinesRoundTripWithoutScriptSerialization() {
        when(repository.findBySettingKey(SitePresentationService.SETTING_KEY)).thenReturn(Optional.of(stored));
        String description = "First line \"quoted\"\n<script>alert(1)</script>";
        var response = update(withSection("hero", new SitePresentationSection(true,
                Map.of("description", Map.of("en", description)), Map.of())));
        assertThat(response.settings().sections().get("hero").texts().get("description")).containsEntry("en", description);
    }

    @Test
    void persistenceFailureIsNotReportedAsSuccessOrAudited() {
        when(repository.findBySettingKey(SitePresentationService.SETTING_KEY)).thenReturn(Optional.of(stored));
        when(repository.saveAndFlush(stored)).thenThrow(new IllegalStateException("Database unavailable"));
        assertThatThrownBy(() -> update(SitePresentationSettings.defaults())).isInstanceOf(IllegalStateException.class);
        verify(audit, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void databaseReadFailureDoesNotBecomeDefaultSuccess() {
        when(repository.findBySettingKey(SitePresentationService.SETTING_KEY)).thenThrow(new IllegalStateException("Offline"));
        assertThatThrownBy(service::current).isInstanceOf(IllegalStateException.class);
    }

    private SitePresentationSettings withSection(String id, SitePresentationSection section) {
        return new SitePresentationSettings(null, null, null, Map.of(id, section), Map.of());
    }
    private com.iflytek.skillhub.dto.SitePresentationResponse update(SitePresentationSettings settings) {
        return service.update(new SitePresentationUpdateRequest(settings, 0L), "admin", new AuditRequestContext("127.0.0.1", "browser"));
    }
    private void invalid(SitePresentationSettings settings) {
        assertThatThrownBy(() -> update(settings)).isInstanceOf(DomainBadRequestException.class);
        verify(repository, never()).saveAndFlush(any());
    }
}
