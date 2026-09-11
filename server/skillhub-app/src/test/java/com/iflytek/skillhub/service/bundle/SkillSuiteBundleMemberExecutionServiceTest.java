package com.iflytek.skillhub.service.bundle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.service.SkillReviewSubmitService;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleCoordinate;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperation;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperationRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResult;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultStatus;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberSourceType;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMode;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePublishAction;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleRelationshipChange;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillSuiteBundleMemberExecutionServiceTest {

    @Test
    void exactPublishedReferenceCompletesWithoutStorageOrPublishSideEffects() {
        Instant now = Instant.parse("2026-09-11T08:00:00Z");
        SkillSuiteBundleExecutionOperationRepository operationRepository =
                mock(SkillSuiteBundleExecutionOperationRepository.class);
        SkillSuiteBundleMemberResultRepository memberRepository = mock(SkillSuiteBundleMemberResultRepository.class);
        SkillSuiteBundleActorContextService actorContextService = mock(SkillSuiteBundleActorContextService.class);
        NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
        SkillRepository skillRepository = mock(SkillRepository.class);
        SkillVersionRepository versionRepository = mock(SkillVersionRepository.class);
        VisibilityChecker visibilityChecker = mock(VisibilityChecker.class);
        SkillPublishService publishService = mock(SkillPublishService.class);
        SkillReviewSubmitService reviewSubmitService = mock(SkillReviewSubmitService.class);
        ObjectStorageService storage = mock(ObjectStorageService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        SkillSuiteBundleExecutionOperation operation = new SkillSuiteBundleExecutionOperation(
                "operation", "preview", "request", "actor", SkillSuiteBundleMode.CREATE,
                1L, "suite", null, null, "1.0.0", "archive", "a".repeat(64),
                Map.of("plan", "value"), "digest", now.minusSeconds(1));
        SkillSuiteBundleMemberResult member = new SkillSuiteBundleMemberResult(
                "operation", 0, new SkillSuiteBundleCoordinate("global", "member"),
                SkillSuiteBundleMemberSourceType.REFERENCE, null, SkillVisibility.PUBLIC, "1.0.0",
                SkillSuiteBundleRelationshipChange.ADDED, SkillSuiteBundlePublishAction.REFERENCE_VERSION,
                "sha256:fingerprint", 11L, 12L, List.of(), List.of(), now.minusSeconds(1));
        SkillSuiteBundlePreviewPlanner.MemberPlan memberPlan = new SkillSuiteBundlePreviewPlanner.MemberPlan(
                new SkillSuiteBundleCoordinate("global", "member"), SkillSuiteBundleMemberSourceType.REFERENCE,
                SkillSuiteBundleRelationshipChange.ADDED, SkillSuiteBundlePublishAction.REFERENCE_VERSION,
                11L, 12L, SkillVisibility.PUBLIC, "1.0.0", "sha256:fingerprint",
                List.of(), List.of(), List.of());
        SkillSuiteBundlePreviewPlanner.PreviewPlan plan = new SkillSuiteBundlePreviewPlanner.PreviewPlan(
                SkillSuiteBundleMode.CREATE, new SkillSuiteBundleCoordinate("global", "suite"),
                1L, null, null, "1.0.0", "Suite", "Summary", "Overview",
                SkillVisibility.PUBLIC, List.of(memberPlan), List.of(), List.of(), List.of(), "digest");
        Skill skill = mock(Skill.class);
        SkillVersion version = mock(SkillVersion.class);
        Namespace namespace = mock(Namespace.class);
        when(operationRepository.findByIdForUpdate("operation")).thenReturn(Optional.of(operation));
        when(memberRepository.findByOperationIdOrderByPositionForUpdate("operation")).thenReturn(List.of(member));
        when(actorContextService.requireCurrent("actor")).thenReturn(
                new SkillSuiteBundleActorContextService.ActorContext(Map.of(), Set.of()));
        when(objectMapper.convertValue(operation.getPlan(), SkillSuiteBundlePreviewPlanner.PreviewPlan.class))
                .thenReturn(plan);
        when(skillRepository.findById(11L)).thenReturn(Optional.of(skill));
        when(versionRepository.findById(12L)).thenReturn(Optional.of(version));
        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(namespace));
        when(namespace.getId()).thenReturn(1L);
        when(namespace.getStatus()).thenReturn(NamespaceStatus.ACTIVE);
        when(skill.getId()).thenReturn(11L);
        when(skill.getNamespaceId()).thenReturn(1L);
        when(skill.getSlug()).thenReturn("member");
        when(skill.getStatus()).thenReturn(SkillStatus.ACTIVE);
        when(skill.getVisibility()).thenReturn(SkillVisibility.PUBLIC);
        when(version.getSkillId()).thenReturn(11L);
        when(version.getVersion()).thenReturn("1.0.0");
        when(version.getStatus()).thenReturn(SkillVersionStatus.PUBLISHED);
        when(version.isDownloadReady()).thenReturn(true);
        when(visibilityChecker.canAccess(skill, "actor", Map.of(), Set.of())).thenReturn(true);
        SkillSuiteBundleMemberExecutionService service = new SkillSuiteBundleMemberExecutionService(
                operationRepository, memberRepository, actorContextService, namespaceRepository,
                skillRepository, versionRepository, visibilityChecker, publishService,
                reviewSubmitService, storage, objectMapper, Clock.fixed(now, ZoneOffset.UTC));

        assertThat(service.executeNext("operation"))
                .isEqualTo(SkillSuiteBundleMemberExecutionService.ExecutionOutcome.PROGRESSED);

        assertThat(member.getStatus()).isEqualTo(SkillSuiteBundleMemberResultStatus.COMPLETED);
        verify(publishService, never()).publishBundleMemberFromEntries(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                anyList(), org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anySet(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(storage, never()).getObject(org.mockito.ArgumentMatchers.anyString());
    }
}
