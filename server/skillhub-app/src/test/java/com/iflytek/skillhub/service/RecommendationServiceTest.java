package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.settings.*;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.*;
import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.RecommendationSkillQuery;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;

class RecommendationServiceTest {
    private final SystemSettingRepository settings = mock(SystemSettingRepository.class);
    private final RecommendationSkillQuery query = mock(RecommendationSkillQuery.class);
    private final SkillSearchAppService summaries = mock(SkillSearchAppService.class);
    private final RecommendationDefaults defaults = mock(RecommendationDefaults.class);
    private final SitePresentationAssetService assets = mock(SitePresentationAssetService.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final jakarta.validation.ValidatorFactory validators = Validation.buildDefaultValidatorFactory();
    private final RecommendationService service = new RecommendationService(settings, mapper, validators.getValidator(), assets, query, summaries, audit, mock(RequestIdAccessor.class), defaults, mock(SkillLabelProjectionService.class));
    private final AuditRequestContext context = new AuditRequestContext("127.0.0.1", "test");
    private SystemSetting row;
    @BeforeEach void setup() {
        row = new SystemSetting(RecommendationService.KEY, Map.of());
        when(settings.findBySettingKey(RecommendationService.KEY)).thenAnswer(inv -> Optional.of(row));
        when(defaults.content()).thenReturn(RecommendationContent.empty());
        when(query.eligible(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(summaries.mapVisibleSkillSummaries(anyList(), isNull())).thenReturn(List.of());
    }
    @AfterEach void close() { validators.close(); }
    private RecommendationContent content(String title, List<Long> ids) {
        return new RecommendationContent(List.of(new RecommendationTopic("meeting", title, "Description", null, true, ids)), ids);
    }
    @Test void draftDoesNotChangePublishedAndExplicitEmptyNeverResurrectsDefaults() {
        var initial = content("Original", List.of(1L));
        when(defaults.content()).thenReturn(initial);
        service.save(new RecommendationUpdateRequest(0, content("Draft", List.of(2L))), "admin", context);
        assertThat(row.getValue()).containsKey("draft").doesNotContainKey("published");
        assertThat(service.current().published()).isEqualTo(initial);
        service.save(new RecommendationUpdateRequest(0, RecommendationContent.empty()), "admin", context);
        service.publish(0, "admin", context);
        assertThat(service.current().usingDefaults()).isFalse();
        assertThat(service.current().published()).isEqualTo(RecommendationContent.empty());
    }
    @Test void publishUsesSavedDraftAndRechecksCurrentEligibility() {
        service.save(new RecommendationUpdateRequest(0, content("Draft", List.of(1L))), "admin", context);
        when(query.eligible(List.of(1L))).thenReturn(List.of());
        assertThatThrownBy(() -> service.publish(0, "admin", context)).isInstanceOf(DomainConflictException.class);
        assertThat(row.getValue()).doesNotContainKey("published");
        when(query.eligible(List.of(1L))).thenReturn(List.of(1L));
        service.publish(0, "admin", context);
        assertThat(row.getValue().get("published")).isEqualTo(row.getValue().get("draft"));
    }
    @Test void publishRequiresAnExplicitSavedDraft() {
        assertThatThrownBy(() -> service.publish(0, "admin", context)).isInstanceOf(DomainBadRequestException.class);
        verify(settings, never()).saveAndFlush(any());
    }
    @Test void staleVersionDoesNotOverwriteDraft() {
        assertThatThrownBy(() -> service.save(new RecommendationUpdateRequest(12, RecommendationContent.empty()), "admin", context)).isInstanceOf(DomainConflictException.class);
        verify(settings, never()).saveAndFlush(any());
        assertThat(row.getValue()).isEmpty();
    }
    @Test void duplicateIdsEmptyEnabledTopicAndUnownedCoverAreRejected() {
        assertThatThrownBy(() -> service.save(new RecommendationUpdateRequest(0, content("X", List.of(1L, 1L))), "admin", context)).isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> service.save(new RecommendationUpdateRequest(0, content(" ", List.of(1L))), "admin", context)).isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> service.save(new RecommendationUpdateRequest(0, content("X", List.of())), "admin", context)).isInstanceOf(DomainBadRequestException.class);
        var bad = new RecommendationContent(List.of(new RecommendationTopic("x", "X", "", "https://example.test/cover", true, List.of(1L))), List.of());
        doThrow(new DomainBadRequestException("invalid")).when(assets).validateReference("https://example.test/cover");
        assertThatThrownBy(() -> service.save(new RecommendationUpdateRequest(0, bad), "admin", context)).isInstanceOf(DomainBadRequestException.class);
        verify(settings, never()).saveAndFlush(any());
    }
    @Test void publicProjectionDropsUnavailableTopicsIdsAndCounts() {
        var c = content("PRIVATE_TITLE_MUST_NOT_LEAK", List.of(9L));
        row = new SystemSetting(RecommendationService.KEY, Map.of("published", mapper.convertValue(c, Map.class)));
        when(query.eligible(List.of(9L))).thenReturn(List.of());
        assertThat(service.published()).isEqualTo(new RecommendationPublicResponse(List.of(), List.of()));
        assertThatThrownBy(() -> service.topic("meeting")).isInstanceOf(DomainNotFoundException.class);
        verify(summaries, never()).mapVisibleSkillSummaries(eq(List.of(9L)), any());
    }
    @Test void candidateLimitsRejectUnboundedQueries() {
        assertThatThrownBy(() -> service.candidates("", -1, 20)).isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> service.candidates("", 0, 1000)).isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> service.candidates("x".repeat(101), 0, 20)).isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> service.candidates("", 0, 20, "x".repeat(101))).isInstanceOf(DomainBadRequestException.class);
        verify(query, never()).candidates(anyString(), anyInt(), anyInt());
        verify(query, never()).candidates(anyString(), anyInt(), anyInt(), anyString());
    }
    @Test void coverStyleSurvivesDraftPublishAndPublicProjection() {
        var content = new RecommendationContent(List.of(new RecommendationTopic("meeting", "Meeting", "Description", null, "office", true, List.of(1L))), List.of());
        when(summaries.mapVisibleSkillSummaries(List.of(1L), null)).thenReturn(List.of(summary(1L)));
        assertThat(service.save(new RecommendationUpdateRequest(0, content), "admin", context).draft().topics().getFirst().coverStyle()).isEqualTo("office");
        service.publish(0, "admin", context);
        assertThat(service.current().published()).isEqualTo(content);
        assertThat(service.published().topics().getFirst().coverStyle()).isEqualTo("office");
        assertThat(service.topic("meeting").coverStyle()).isEqualTo("office");
    }
    @Test void legacyJsonWithoutCoverStyleIsStillReadable() {
        row = new SystemSetting(RecommendationService.KEY, Map.of("published", Map.of("topics", List.of(
                Map.of("id", "legacy", "title", "Legacy", "description", "", "enabled", false, "skillIds", List.of())), "featuredSkillIds", List.of())));
        assertThat(service.current().published().topics().getFirst().coverStyle()).isNull();
    }
    @Test void coverStyleRejectsUnknownValuesWithoutPersisting() {
        var invalid = new RecommendationContent(List.of(new RecommendationTopic("meeting", "Meeting", "", null, "custom-css", true, List.of(1L))), List.of());
        assertThatThrownBy(() -> service.save(new RecommendationUpdateRequest(0, invalid), "admin", context)).isInstanceOf(DomainBadRequestException.class);
        assertThat(row.getValue()).isEmpty();
        verify(settings, never()).saveAndFlush(any());
    }
    @Test void candidatesUseTheSameTrimmedLabelForItemsAndCount() {
        when(query.candidates("Meeting", 1, 20, "productivity")).thenReturn(List.of(1L));
        when(query.count("Meeting", "productivity")).thenReturn(21L);
        when(summaries.mapVisibleSkillSummaries(List.of(1L), null)).thenReturn(List.of(summary(1L)));
        var page = service.candidates(" Meeting ", 1, 20, " productivity ");
        assertThat(page.items()).extracting(SkillSummaryResponse::id).containsExactly(1L);
        assertThat(page.total()).isEqualTo(21L);
        verify(query).candidates("Meeting", 1, 20, "productivity");
        verify(query).count("Meeting", "productivity");
    }
    private SkillSummaryResponse summary(long id) {
        return new SkillSummaryResponse(id, "meeting", "Meeting", "Summary", "PUBLIC", "ACTIVE",
                0L, 0, BigDecimal.ZERO, 0, "global", Instant.now(), false, null, null, null, "PUBLISHED", null);
    }
}
