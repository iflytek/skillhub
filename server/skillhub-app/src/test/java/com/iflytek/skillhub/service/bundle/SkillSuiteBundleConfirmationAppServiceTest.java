package com.iflytek.skillhub.service.bundle;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.SkillSuiteBundleProperties;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleCoordinate;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperation;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperationRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleManifest;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleManifestParser;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResult;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberSourceType;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMode;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSession;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSessionRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewStatus;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePublishAction;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleRelationshipChange;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillSuiteBundleConfirmationAppServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-11T08:00:00Z");
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() { };

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SkillSuiteBundlePreviewSessionRepository previewRepository;
    private SkillSuiteBundleExecutionOperationRepository operationRepository;
    private SkillSuiteBundleMemberResultRepository memberRepository;
    private SkillSuiteBundlePreviewPlanner planner;
    private ObjectStorageService objectStorageService;
    private SkillSuiteBundleProperties properties;
    private SkillSuiteBundleConfirmationAppService service;

    @BeforeEach
    void setUp() {
        previewRepository = mock(SkillSuiteBundlePreviewSessionRepository.class);
        operationRepository = mock(SkillSuiteBundleExecutionOperationRepository.class);
        memberRepository = mock(SkillSuiteBundleMemberResultRepository.class);
        planner = mock(SkillSuiteBundlePreviewPlanner.class);
        objectStorageService = mock(ObjectStorageService.class);
        when(objectStorageService.exists("archive.zip")).thenReturn(true);
        properties = new SkillSuiteBundleProperties();
        properties.setConfirmationEnabled(true);
        service = new SkillSuiteBundleConfirmationAppService(
                previewRepository, operationRepository, memberRepository, planner, objectStorageService, properties,
                objectMapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void confirmsExactLivePlanAndCreatesReservationBeforeMemberResults() {
        SkillSuiteBundleManifest manifest = manifest();
        SkillSuiteBundlePreviewPlanner.PreviewPlan plan = plan();
        SkillSuiteBundlePreviewSession preview = preview(manifest, plan);
        when(operationRepository.findByActorIdAndClientRequestId("actor", "request-1"))
                .thenReturn(Optional.empty());
        when(previewRepository.findByIdForUpdate("preview-1")).thenReturn(Optional.of(preview));
        when(planner.plan(any(), any(), any(), any())).thenReturn(plan);

        SkillSuiteBundleConfirmationAppService.ConfirmationOutcome outcome = service.confirm(
                "preview-1", "request-1", "warning-digest", "actor", Map.of(), Set.of());

        assertThat(outcome.status()).isEqualTo("RUNNING");
        assertThat(outcome.replayed()).isFalse();
        assertThat(preview.getStatus()).isEqualTo(SkillSuiteBundlePreviewStatus.CONFIRMED);
        verify(operationRepository).flush();
        verify(memberRepository).flush();
        verify(previewRepository).flush();

        ArgumentCaptor<List<SkillSuiteBundleMemberResult>> members = ArgumentCaptor.forClass(List.class);
        verify(memberRepository).saveAll(members.capture());
        assertThat(members.getValue()).singleElement().satisfies(member -> {
            assertThat(member.getNamespaceSlug()).isEqualTo("global");
            assertThat(member.getSkillSlug()).isEqualTo("member");
            assertThat(member.getPackagePath()).isEqualTo("skills/member");
            assertThat(member.getPublishAction()).isEqualTo(SkillSuiteBundlePublishAction.CREATE_SKILL);
        });

        ArgumentCaptor<SkillSuiteBundlePackageAnalyzer.BundleAnalysis> analysis =
                ArgumentCaptor.forClass(SkillSuiteBundlePackageAnalyzer.BundleAnalysis.class);
        verify(planner).plan(analysis.capture(), any(), any(), any());
        assertThat(analysis.getValue().packageMembers()).singleElement().satisfies(member -> {
            assertThat(member.directory()).isEqualTo("skills/member");
            assertThat(member.metadata().version()).isEqualTo("1.0.0");
            assertThat(member.fingerprint()).isEqualTo("sha256:member");
        });
    }

    @Test
    void repeatsSameActorRequestWithoutLockingOrCreatingAnotherOperation() {
        SkillSuiteBundleExecutionOperation existing = operation("preview-1", "request-1");
        when(operationRepository.findByActorIdAndClientRequestId("actor", "request-1"))
                .thenReturn(Optional.of(existing));

        SkillSuiteBundleConfirmationAppService.ConfirmationOutcome outcome = service.confirm(
                "preview-1", "request-1", "warning-digest", "actor", Map.of(), Set.of());

        assertThat(outcome.operationId()).isEqualTo("operation-1");
        assertThat(outcome.replayed()).isTrue();
        verify(previewRepository, never()).findByIdForUpdate(any());
        verify(operationRepository, never()).save(any());
    }

    @Test
    void rejectsChangedLivePlanBeforeAcquiringReservation() {
        SkillSuiteBundlePreviewPlanner.PreviewPlan original = plan();
        SkillSuiteBundlePreviewPlanner.PreviewPlan changed = new SkillSuiteBundlePreviewPlanner.PreviewPlan(
                original.mode(), original.target(), original.targetNamespaceId(), original.targetSuiteId(),
                original.baseSuiteVersionId(), original.targetVersion(), original.displayName(),
                original.summary(), original.overview(), original.visibility(), original.members(),
                original.removedMembers(), List.of("state changed"), original.warnings(), original.warningDigest());
        when(operationRepository.findByActorIdAndClientRequestId(any(), any())).thenReturn(Optional.empty());
        when(previewRepository.findByIdForUpdate("preview-1"))
                .thenReturn(Optional.of(preview(manifest(), original)));
        when(planner.plan(any(), any(), any(), any())).thenReturn(changed);

        assertThatThrownBy(() -> service.confirm(
                "preview-1", "request-1", "warning-digest", "actor", Map.of(), Set.of()))
                .isInstanceOf(DomainBadRequestException.class)
                .extracting("messageCode")
                .isEqualTo("error.suite.bundle.preview.stateChanged");
        verify(operationRepository, never()).save(any());
        verify(memberRepository, never()).saveAll(any());
    }

    @Test
    void mapsReservationRaceToConflictAndTransactionDoesNotReachMembers() {
        SkillSuiteBundlePreviewPlanner.PreviewPlan plan = plan();
        when(operationRepository.findByActorIdAndClientRequestId(any(), any())).thenReturn(Optional.empty());
        when(previewRepository.findByIdForUpdate("preview-1"))
                .thenReturn(Optional.of(preview(manifest(), plan)));
        when(planner.plan(any(), any(), any(), any())).thenReturn(plan);
        doThrow(new DataIntegrityViolationException("reservation collision"))
                .when(operationRepository).flush();

        assertThatThrownBy(() -> service.confirm(
                "preview-1", "request-1", "warning-digest", "actor", Map.of(), Set.of()))
                .isInstanceOf(DomainConflictException.class)
                .extracting("messageCode")
                .isEqualTo("error.suite.bundle.confirmation.operationConflict");
        verify(memberRepository, never()).saveAll(any());
    }

    @Test
    void featureFlagAndIdempotencyKeyAreValidatedBeforeDatabaseWrites() {
        properties.setConfirmationEnabled(false);
        assertThatThrownBy(() -> service.confirm(
                "preview-1", "request-1", "warning-digest", "actor", Map.of(), Set.of()))
                .isInstanceOf(DomainBadRequestException.class)
                .extracting("messageCode")
                .isEqualTo("error.suite.bundle.confirmation.disabled");

        properties.setConfirmationEnabled(true);
        assertThatThrownBy(() -> service.confirm(
                "preview-1", "not valid!", "warning-digest", "actor", Map.of(), Set.of()))
                .isInstanceOf(DomainBadRequestException.class)
                .extracting("messageCode")
                .isEqualTo("error.suite.bundle.confirmation.idempotencyKey.invalid");
        verify(operationRepository, never()).save(any());
    }

    @Test
    void missingImmutableArchiveRequiresNewPreviewBeforeReplanning() {
        SkillSuiteBundlePreviewPlanner.PreviewPlan plan = plan();
        when(operationRepository.findByActorIdAndClientRequestId(any(), any())).thenReturn(Optional.empty());
        when(previewRepository.findByIdForUpdate("preview-1"))
                .thenReturn(Optional.of(preview(manifest(), plan)));
        when(objectStorageService.exists("archive.zip")).thenReturn(false);

        assertThatThrownBy(() -> service.confirm(
                "preview-1", "request-1", "warning-digest", "actor", Map.of(), Set.of()))
                .isInstanceOf(DomainBadRequestException.class)
                .extracting("messageCode")
                .isEqualTo("error.suite.bundle.preview.stateChanged");
        verify(planner, never()).plan(any(), any(), any(), any());
        verify(operationRepository, never()).save(any());
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

    private SkillSuiteBundleExecutionOperation operation(String previewToken, String requestId) {
        return new SkillSuiteBundleExecutionOperation(
                "operation-1", previewToken, requestId, "actor", SkillSuiteBundleMode.CREATE,
                1L, "suite", null, null, "1.0.0", "archive.zip", "a".repeat(64),
                Map.of("plan", "value"), "warning-digest", NOW);
    }

    private SkillSuiteBundlePreviewPlanner.PreviewPlan plan() {
        SkillSuiteBundlePreviewPlanner.MemberPlan member = new SkillSuiteBundlePreviewPlanner.MemberPlan(
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
