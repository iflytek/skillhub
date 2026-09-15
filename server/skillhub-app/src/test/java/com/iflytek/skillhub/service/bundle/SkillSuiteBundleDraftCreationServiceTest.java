package com.iflytek.skillhub.service.bundle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.suite.SkillSuite;
import com.iflytek.skillhub.domain.suite.SkillSuiteDraftService;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersion;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleCoordinate;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperation;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperationRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleManifest;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMember;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResult;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberSourceType;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMode;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleOperationStatus;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSession;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSessionRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePublishAction;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleRelationshipChange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillSuiteBundleDraftCreationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-11T08:00:00Z");
    private SkillSuiteBundleExecutionOperationRepository operationRepository;
    private SkillSuiteBundleMemberResultRepository memberRepository;
    private SkillSuiteBundlePreviewSessionRepository previewRepository;
    private SkillSuiteDraftService draftService;
    private ObjectMapper objectMapper;
    private SkillSuiteBundleDraftCreationService service;

    @BeforeEach
    void setUp() {
        operationRepository = mock(SkillSuiteBundleExecutionOperationRepository.class);
        memberRepository = mock(SkillSuiteBundleMemberResultRepository.class);
        previewRepository = mock(SkillSuiteBundlePreviewSessionRepository.class);
        SkillSuiteBundleActorContextService actorContextService = mock(SkillSuiteBundleActorContextService.class);
        draftService = mock(SkillSuiteDraftService.class);
        objectMapper = mock(ObjectMapper.class);
        when(actorContextService.requireCurrent("actor")).thenReturn(
                new SkillSuiteBundleActorContextService.ActorContext(Map.of(), Set.of("SUPER_ADMIN")));
        service = new SkillSuiteBundleDraftCreationService(
                operationRepository, memberRepository, previewRepository, actorContextService,
                draftService, objectMapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void doesNotCreatePartialSuiteWhileAnyMemberIsUnfinished() {
        when(operationRepository.findByIdForUpdate("operation")).thenReturn(Optional.of(operation()));
        when(memberRepository.findByOperationIdOrderByPositionForUpdate("operation"))
                .thenReturn(List.of(member(false)));

        assertThat(service.create("operation")).isFalse();

        verify(draftService, never()).create(any(), any());
        verify(draftService, never()).createVersion(any(), any(), any());
    }

    @Test
    void atomicallyRecordsTheDraftCreatedFromExactCompletedMemberIds() {
        SkillSuiteBundleExecutionOperation operation = operation();
        SkillSuiteBundleMemberResult member = member(true);
        SkillSuiteBundlePreviewSession preview = mock(SkillSuiteBundlePreviewSession.class);
        Map<String, Object> manifestJson = Map.of("manifest", "value");
        when(preview.getManifest()).thenReturn(manifestJson);
        when(operationRepository.findByIdForUpdate("operation")).thenReturn(Optional.of(operation));
        when(memberRepository.findByOperationIdOrderByPositionForUpdate("operation"))
                .thenReturn(List.of(member));
        when(previewRepository.findById("preview")).thenReturn(Optional.of(preview));
        when(objectMapper.convertValue(manifestJson, SkillSuiteBundleManifest.class)).thenReturn(manifest());
        when(objectMapper.convertValue(operation.getPlan(), SkillSuiteBundlePreviewPlanner.PreviewPlan.class))
                .thenReturn(plan());
        SkillSuite suite = mock(SkillSuite.class);
        SkillSuiteVersion version = mock(SkillSuiteVersion.class);
        when(suite.getId()).thenReturn(21L);
        when(version.getId()).thenReturn(22L);
        when(draftService.create(any(), any())).thenReturn(
                new SkillSuiteDraftService.CreatedDraft(suite, version, List.of()));

        assertThat(service.create("operation")).isTrue();

        assertThat(operation.getStatus()).isEqualTo(SkillSuiteBundleOperationStatus.SUITE_DRAFT_CREATED);
        assertThat(operation.getResultSuiteId()).isEqualTo(21L);
        assertThat(operation.getResultSuiteVersionId()).isEqualTo(22L);
        assertThat(operation.isReservationActive()).isFalse();
        verify(operationRepository).flush();
    }

    private SkillSuiteBundleExecutionOperation operation() {
        Map<String, Object> planJson = Map.of("plan", "value");
        return new SkillSuiteBundleExecutionOperation(
                "operation", "preview", "request", "actor", SkillSuiteBundleMode.CREATE,
                1L, "suite", null, null, "1.0.0", "archive", "a".repeat(64),
                planJson, "digest", NOW.minusSeconds(1));
    }

    private SkillSuiteBundleMemberResult member(boolean completed) {
        SkillSuiteBundleMemberResult member = new SkillSuiteBundleMemberResult(
                "operation", 0, new SkillSuiteBundleCoordinate("global", "member"),
                SkillSuiteBundleMemberSourceType.REFERENCE, null, SkillVisibility.PUBLIC, "1.0.0",
                SkillSuiteBundleRelationshipChange.ADDED, SkillSuiteBundlePublishAction.REFERENCE_VERSION,
                "sha256:fingerprint", 11L, 12L, List.of(), List.of(), NOW.minusSeconds(1));
        if (completed) {
            member.start(NOW.minusMillis(500));
            member.markCompleted(NOW.minusMillis(400));
        }
        return member;
    }

    private SkillSuiteBundleManifest manifest() {
        SkillSuiteBundleCoordinate suite = new SkillSuiteBundleCoordinate("global", "suite");
        SkillSuiteBundleCoordinate entry = new SkillSuiteBundleCoordinate("global", "member");
        return new SkillSuiteBundleManifest(
                SkillSuiteBundleManifest.API_VERSION, SkillSuiteBundleManifest.KIND,
                new SkillSuiteBundleManifest.Metadata(suite),
                new SkillSuiteBundleManifest.Spec(
                        SkillSuiteBundleMode.CREATE, null, "1.0.0", "Suite", "Summary",
                        "Overview", SkillVisibility.PUBLIC, "Initial", entry,
                        List.of(new SkillSuiteBundleMember(
                                entry, null, new SkillSuiteBundleMember.ReferenceSource("1.0.0")))));
    }

    private SkillSuiteBundlePreviewPlanner.PreviewPlan plan() {
        return new SkillSuiteBundlePreviewPlanner.PreviewPlan(
                SkillSuiteBundleMode.CREATE, new SkillSuiteBundleCoordinate("global", "suite"),
                1L, null, null, "1.0.0", "Suite", "Summary", "Overview",
                SkillVisibility.PUBLIC, List.of(), List.of(), List.of(), List.of(), "digest");
    }
}
