package com.iflytek.skillhub.service.bundle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.SkillSuiteBundleProperties;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.validation.ValidationResult;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleCoordinate;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleManifest;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleManifestParser;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMode;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillSuiteBundlePreviewAppServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-11T08:00:00Z");

    private SkillSuiteBundleArchiveService archiveService;
    private SkillSuiteBundlePreviewPlanner planner;
    private SkillSuiteBundlePreviewPersistenceService persistenceService;
    private SkillSuiteBundlePreviewAppService service;

    @BeforeEach
    void setUp() {
        archiveService = mock(SkillSuiteBundleArchiveService.class);
        planner = mock(SkillSuiteBundlePreviewPlanner.class);
        persistenceService = mock(SkillSuiteBundlePreviewPersistenceService.class);
        SkillSuiteBundleProperties properties = new SkillSuiteBundleProperties();
        properties.setPreviewTtl(Duration.ofMinutes(30));
        service = new SkillSuiteBundlePreviewAppService(
                archiveService, planner, persistenceService, properties,
                new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void persistsActorBoundPreviewWithoutCreatingAnExecutionReservation() throws Exception {
        SkillSuiteBundlePackageAnalyzer.BundleAnalysis packageAnalysis = packageAnalysis(List.of());
        SkillSuiteBundleArchiveService.StagedBundleAnalysis staged = staged(packageAnalysis);
        SkillSuiteBundlePreviewPlanner.PreviewPlan plan = validPlan();
        when(archiveService.stageAndAnalyze(any())).thenReturn(staged);
        when(planner.plan(packageAnalysis, "actor", Map.of(1L, NamespaceRole.MEMBER), Set.of()))
                .thenReturn(plan);
        SkillSuiteBundlePreviewAppService.PreviewOutcome result = service.preview(
                upload(), "actor", Map.of(1L, NamespaceRole.MEMBER), Set.of());

        assertThat(result.confirmable()).isTrue();
        assertThat(result.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        ArgumentCaptor<SkillSuiteBundlePreviewSession> captor =
                ArgumentCaptor.forClass(SkillSuiteBundlePreviewSession.class);
        verify(persistenceService).save(captor.capture());
        SkillSuiteBundlePreviewSession stored = captor.getValue();
        assertThat(stored.getActorId()).isEqualTo("actor");
        assertThat(stored.getNamespaceId()).isEqualTo(1L);
        assertThat(stored.getTargetSuiteSlug()).isEqualTo("preview-suite");
        assertThat(stored.getArchiveObjectKey()).isEqualTo("temporary/archive.zip");
        assertThat(stored.getArchiveSha256()).isEqualTo("a".repeat(64));
        assertThat(stored.getWarningDigest()).isEqualTo("b".repeat(64));
        assertThat(stored.getPlan()).containsEntry("targetVersion", "1.0.0");
        assertThat(stored.getExpiresAt()).isEqualTo(result.expiresAt());
        verify(archiveService, never()).cleanupStagedObjects(staged.objectKeys());
    }

    @Test
    void invalidSemanticPlanReturnsErrorsAndDeletesAllStagedObjects() throws Exception {
        SkillSuiteBundlePackageAnalyzer.BundleAnalysis packageAnalysis = packageAnalysis(List.of());
        SkillSuiteBundleArchiveService.StagedBundleAnalysis staged = staged(packageAnalysis);
        SkillSuiteBundlePreviewPlanner.PreviewPlan invalid = new SkillSuiteBundlePreviewPlanner.PreviewPlan(
                SkillSuiteBundleMode.CREATE, new SkillSuiteBundleCoordinate("global", "preview-suite"),
                1L, null, null, "1.0.0", "Preview Suite", "Summary", "Overview",
                SkillVisibility.PUBLIC, List.of(), List.of(), List.of("blocked"), List.of(), "b".repeat(64));
        when(archiveService.stageAndAnalyze(any())).thenReturn(staged);
        when(planner.plan(any(), any(), any(), any())).thenReturn(invalid);

        SkillSuiteBundlePreviewAppService.PreviewOutcome result = service.preview(
                upload(), "actor", Map.of(), Set.of());

        assertThat(result.confirmable()).isFalse();
        assertThat(result.errors()).containsExactly("blocked");
        verify(archiveService).cleanupStagedObjects(staged.objectKeys());
        verify(persistenceService, never()).save(any());
    }

    @Test
    void persistenceFailureRollsBackPreviewAndDeletesAllStagedObjects() throws Exception {
        SkillSuiteBundlePackageAnalyzer.BundleAnalysis packageAnalysis = packageAnalysis(List.of());
        SkillSuiteBundleArchiveService.StagedBundleAnalysis staged = staged(packageAnalysis);
        when(archiveService.stageAndAnalyze(any())).thenReturn(staged);
        when(planner.plan(any(), any(), any(), any())).thenReturn(validPlan());
        doThrow(new IllegalStateException("database unavailable")).when(persistenceService).save(any());

        assertThatThrownBy(() -> service.preview(upload(), "actor", Map.of(), Set.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
        verify(archiveService).cleanupStagedObjects(staged.objectKeys());
    }

    @Test
    void structuralMemberErrorsAreReturnedWithTheirCoordinate() throws Exception {
        SkillSuiteBundlePackageAnalyzer.MemberPackageAnalysis invalidMember =
                new SkillSuiteBundlePackageAnalyzer.MemberPackageAnalysis(
                        new SkillSuiteBundleCoordinate("global", "reference"), "skills/reference",
                        null, ValidationResult.fail("SKILL.md is required"), List.of(), "sha256:invalid");
        SkillSuiteBundlePackageAnalyzer.BundleAnalysis packageAnalysis = new
                SkillSuiteBundlePackageAnalyzer.BundleAnalysis(
                        packageAnalysis(List.of()).manifest(), List.of(invalidMember), List.of());
        SkillSuiteBundleArchiveService.StagedBundleAnalysis staged = staged(packageAnalysis);
        when(archiveService.stageAndAnalyze(any())).thenReturn(staged);

        SkillSuiteBundlePreviewAppService.PreviewOutcome result = service.preview(
                upload(), "actor", Map.of(), Set.of());

        assertThat(result.confirmable()).isFalse();
        assertThat(result.errors()).containsExactly("@global/reference: SKILL.md is required");
        verify(planner, never()).plan(any(), any(), any(), any());
        verify(persistenceService, never()).save(any());
    }

    private SkillSuiteBundleArchiveService.StagedBundleAnalysis staged(
            SkillSuiteBundlePackageAnalyzer.BundleAnalysis packageAnalysis
    ) {
        return new SkillSuiteBundleArchiveService.StagedBundleAnalysis(
                "temporary/archive.zip", "a".repeat(64), packageAnalysis,
                List.of("temporary/archive.zip", "temporary/member-file"));
    }

    private SkillSuiteBundlePreviewPlanner.PreviewPlan validPlan() {
        return new SkillSuiteBundlePreviewPlanner.PreviewPlan(
                SkillSuiteBundleMode.CREATE, new SkillSuiteBundleCoordinate("global", "preview-suite"),
                1L, null, null, "1.0.0", "Preview Suite", "Summary", "Overview",
                SkillVisibility.PUBLIC, List.of(), List.of(), List.of(), List.of(), "b".repeat(64));
    }

    private SkillSuiteBundlePackageAnalyzer.BundleAnalysis packageAnalysis(List<String> errors) {
        SkillSuiteBundleManifest manifest = new SkillSuiteBundleManifestParser().parse("""
                apiVersion: skillhub.iflytek.com/v1alpha1
                kind: SkillSuiteBundle
                metadata:
                  namespace: global
                  slug: preview-suite
                spec:
                  mode: CREATE
                  version: 1.0.0
                  displayName: Preview Suite
                  summary: Summary
                  overview: Overview
                  visibility: PUBLIC
                  entry: "@global/reference"
                  members:
                    - skill: "@global/reference"
                      reference:
                        version: 1.0.0
                """);
        return new SkillSuiteBundlePackageAnalyzer.BundleAnalysis(manifest, List.of(), errors);
    }

    private MockMultipartFile upload() {
        return new MockMultipartFile("file", "bundle.zip", "application/zip", new byte[]{1});
    }
}
