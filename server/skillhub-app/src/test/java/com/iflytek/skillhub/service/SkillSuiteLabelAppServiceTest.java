package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.label.SkillSuiteLabel;
import com.iflytek.skillhub.domain.label.SkillSuiteLabelService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.suite.SkillSuite;
import com.iflytek.skillhub.domain.suite.SkillSuiteQueryService;
import com.iflytek.skillhub.domain.suite.SkillSuiteRepository;
import com.iflytek.skillhub.dto.SkillLabelDto;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SkillSuiteLabelAppServiceTest {

    private final NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
    private final SkillSuiteRepository suiteRepository = mock(SkillSuiteRepository.class);
    private final SkillSuiteQueryService suiteQueryService = mock(SkillSuiteQueryService.class);
    private final SkillSuiteLabelService suiteLabelService = mock(SkillSuiteLabelService.class);
    private final SkillSuiteLabelProjectionService projectionService =
            mock(SkillSuiteLabelProjectionService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final RequestIdAccessor requestIdAccessor = new RequestIdAccessor();
    private final SkillSuiteLabelAppService service = new SkillSuiteLabelAppService(
            namespaceRepository, suiteRepository, suiteQueryService, suiteLabelService,
            projectionService, auditLogService, requestIdAccessor);

    private Namespace namespace;
    private SkillSuite suite;

    @BeforeEach
    void setUp() {
        namespace = new Namespace("global", "Global", "owner");
        suite = new SkillSuite(1L, "starter", "Starter", "author");
        ReflectionTestUtils.setField(namespace, "id", 1L);
        ReflectionTestUtils.setField(suite, "id", 10L);
        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(namespace));
        when(suiteRepository.findByNamespaceIdAndSlug(1L, "starter")).thenReturn(Optional.of(suite));
    }

    @Test
    void publicReaderMustPassExistingSuiteVisibilityCheck() {
        SkillLabelDto label = new SkillLabelDto("automation", "RECOMMENDED", "Automation");
        when(projectionService.labelsBySuiteIds(List.of(10L))).thenReturn(Map.of(10L, List.of(label)));

        assertThat(service.listLabels("global", "starter", null, Map.of(), Set.of()))
                .containsExactly(label);

        verify(suiteQueryService).getDetail("global", "starter", null, null, Map.of(), Set.of());
    }

    @Test
    void suiteManagerCanReadDraftContainerLabelsWithoutPublishedVersion() {
        when(projectionService.labelsBySuiteIds(List.of(10L))).thenReturn(Map.of());

        assertThat(service.listLabels(
                "global", "starter", "owner", Map.of(1L, NamespaceRole.OWNER), Set.of()))
                .isEmpty();

        verify(suiteQueryService, never()).getDetail(any(), any(), any(), any(), any(), any());
    }

    @Test
    void mutationRecordsSuiteScopedAuditWithRequestCorrelation() {
        SkillSuiteLabel assignment = new SkillSuiteLabel(10L, 20L, "author");
        SkillLabelDto label = new SkillLabelDto("automation", "RECOMMENDED", "Automation");
        when(suiteLabelService.attachLabel(
                10L, "automation", "author", Map.of(1L, NamespaceRole.MEMBER), Set.of()))
                .thenReturn(assignment);
        when(projectionService.labelsBySuiteIds(List.of(10L)))
                .thenReturn(Map.of(10L, List.of(label)));

        try (RequestIdAccessor.Scope ignored = requestIdAccessor.open("req-suite-label")) {
            assertThat(service.attachLabel(
                    "global", "starter", "automation", "author",
                    Map.of(1L, NamespaceRole.MEMBER), Set.of(),
                    new AuditRequestContext("127.0.0.1", "test-agent")))
                    .isEqualTo(label);
        }

        verify(auditLogService).record(
                eq("author"), eq("SKILL_SUITE_LABEL_ATTACH"), eq("SKILL_SUITE"), eq(10L),
                eq("req-suite-label"), eq("127.0.0.1"), eq("test-agent"),
                eq("{\"labelSlug\":\"automation\"}"));
    }
}
