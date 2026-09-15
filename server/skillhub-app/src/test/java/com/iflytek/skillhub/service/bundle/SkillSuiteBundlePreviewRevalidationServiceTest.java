package com.iflytek.skillhub.service.bundle;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleCoordinate;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleManifest;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleManifestParser;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberSourceType;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMode;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSession;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePublishAction;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleRelationshipChange;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillSuiteBundlePreviewRevalidationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-11T08:00:00Z");
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() { };

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SkillSuiteBundlePreviewPlanner planner;
    private ObjectStorageService objectStorageService;
    private SkillSuiteBundlePreviewRevalidationService service;

    @BeforeEach
    void setUp() {
        planner = mock(SkillSuiteBundlePreviewPlanner.class);
        objectStorageService = mock(ObjectStorageService.class);
        service = new SkillSuiteBundlePreviewRevalidationService(planner, objectStorageService, objectMapper);
    }

    @Test
    void replansFromPersistedMemberFactsWithoutExtractingTheArchiveAgain() {
        SkillSuiteBundleManifest manifest = manifest();
        SkillSuiteBundlePreviewPlanner.PreviewPlan plan = plan();
        when(objectStorageService.exists("archive.zip")).thenReturn(true);
        when(planner.plan(any(), any(), any(), any())).thenReturn(plan);

        var result = service.requireUnchanged(preview(manifest, plan), "actor", Map.of(), Set.of());

        assertThat(result.plan()).isEqualTo(plan);
        ArgumentCaptor<SkillSuiteBundlePackageAnalyzer.BundleAnalysis> analysis =
                ArgumentCaptor.forClass(SkillSuiteBundlePackageAnalyzer.BundleAnalysis.class);
        verify(planner).plan(analysis.capture(), any(), any(), any());
        verify(objectStorageService).exists("archive.zip");
        assertThat(analysis.getValue().packageMembers()).singleElement().satisfies(member -> {
            assertThat(member.directory()).isEqualTo("skills/member");
            assertThat(member.metadata().version()).isEqualTo("1.0.0");
            assertThat(member.fingerprint()).isEqualTo("sha256:member");
        });
    }

    @Test
    void missingArchiveRequiresANewPreviewBeforePlanning() {
        when(objectStorageService.exists("archive.zip")).thenReturn(false);

        assertStateChanged(preview(manifest(), plan()));

        verify(planner, never()).plan(any(), any(), any(), any());
    }

    @Test
    void changedLivePlanRequiresANewPreview() {
        SkillSuiteBundlePreviewPlanner.PreviewPlan original = plan();
        SkillSuiteBundlePreviewPlanner.PreviewPlan changed = new SkillSuiteBundlePreviewPlanner.PreviewPlan(
                original.mode(), original.target(), original.targetNamespaceId(), original.targetSuiteId(),
                original.baseSuiteVersionId(), original.targetVersion(), original.displayName(),
                original.summary(), original.overview(), original.visibility(), original.members(),
                original.removedMembers(), List.of("state changed"), original.warnings(), original.warningDigest());
        when(objectStorageService.exists("archive.zip")).thenReturn(true);
        when(planner.plan(any(), any(), any(), any())).thenReturn(changed);

        assertStateChanged(preview(manifest(), original));
    }

    private void assertStateChanged(SkillSuiteBundlePreviewSession preview) {
        assertThatThrownBy(() -> service.requireUnchanged(preview, "actor", Map.of(), Set.of()))
                .isInstanceOf(DomainBadRequestException.class)
                .extracting("messageCode")
                .isEqualTo("error.suite.bundle.preview.stateChanged");
    }

    private SkillSuiteBundlePreviewSession preview(
            SkillSuiteBundleManifest manifest,
            SkillSuiteBundlePreviewPlanner.PreviewPlan plan
    ) {
        return new SkillSuiteBundlePreviewSession(
                "preview-1", "actor", SkillSuiteBundleMode.CREATE, 1L, "suite", null, null,
                "1.0.0", "archive.zip", "a".repeat(64),
                objectMapper.convertValue(manifest, JSON_OBJECT), objectMapper.convertValue(plan, JSON_OBJECT),
                "warning-digest", NOW.plusSeconds(300), NOW.minusSeconds(60));
    }

    private SkillSuiteBundlePreviewPlanner.PreviewPlan plan() {
        var member = new SkillSuiteBundlePreviewPlanner.MemberPlan(
                new SkillSuiteBundleCoordinate("global", "member"), SkillSuiteBundleMemberSourceType.PACKAGE,
                SkillSuiteBundleRelationshipChange.ADDED, SkillSuiteBundlePublishAction.CREATE_SKILL,
                null, null, SkillVisibility.PUBLIC, "1.0.0", "sha256:member",
                List.of(new SkillSuiteBundlePackageAnalyzer.StagedMemberFile(
                        "SKILL.md", 100, "text/markdown", "b".repeat(64), "staged/member/SKILL.md")),
                List.of(), List.of());
        return new SkillSuiteBundlePreviewPlanner.PreviewPlan(
                SkillSuiteBundleMode.CREATE, new SkillSuiteBundleCoordinate("global", "suite"),
                1L, null, null, "1.0.0", "Suite", "Summary", "Overview",
                SkillVisibility.PUBLIC, List.of(member), List.of(), List.of(), List.of(), "warning-digest");
    }

    private SkillSuiteBundleManifest manifest() {
        return new SkillSuiteBundleManifestParser().parse("""
                apiVersion: skillhub.iflytek.com/v1alpha1
                kind: SkillSuiteBundle
                metadata:
                  namespace: global
                  slug: suite
                spec:
                  mode: CREATE
                  version: 1.0.0
                  displayName: Suite
                  summary: Summary
                  overview: Overview
                  visibility: PUBLIC
                  entry: "@global/member"
                  members:
                    - skill: "@global/member"
                      package:
                        path: skills/member
                        visibility: PUBLIC
                """);
    }
}
