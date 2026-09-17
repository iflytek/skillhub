package com.iflytek.skillhub.service.bundle;

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
import com.iflytek.skillhub.domain.skill.service.SkillReviewSubmitService;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleCoordinate;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperation;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperationRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResult;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultStatus;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberSourceType;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMode;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleOperationStatus;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillSuiteBundleMemberProgressServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-11T08:00:00Z");
    private SkillSuiteBundleExecutionOperationRepository operationRepository;
    private SkillSuiteBundleMemberResultRepository memberRepository;
    private SkillRepository skillRepository;
    private SkillVersionRepository versionRepository;
    private SkillReviewSubmitService reviewSubmitService;
    private SkillSuiteBundleMemberProgressService service;

    @BeforeEach
    void setUp() {
        operationRepository = mock(SkillSuiteBundleExecutionOperationRepository.class);
        memberRepository = mock(SkillSuiteBundleMemberResultRepository.class);
        SkillSuiteBundleActorContextService actorContextService = mock(SkillSuiteBundleActorContextService.class);
        NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
        skillRepository = mock(SkillRepository.class);
        versionRepository = mock(SkillVersionRepository.class);
        reviewSubmitService = mock(SkillReviewSubmitService.class);
        when(actorContextService.requireCurrent("actor")).thenReturn(
                new SkillSuiteBundleActorContextService.ActorContext(Map.of(), Set.of()));

        Namespace namespace = mock(Namespace.class);
        when(namespace.getId()).thenReturn(1L);
        when(namespace.getStatus()).thenReturn(NamespaceStatus.ACTIVE);
        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(namespace));
        Skill skill = mock(Skill.class);
        when(skill.getId()).thenReturn(11L);
        when(skill.getNamespaceId()).thenReturn(1L);
        when(skill.getSlug()).thenReturn("member");
        when(skill.getOwnerId()).thenReturn("actor");
        when(skill.getStatus()).thenReturn(SkillStatus.ACTIVE);
        when(skill.getVisibility()).thenReturn(SkillVisibility.PUBLIC);
        when(skillRepository.findById(11L)).thenReturn(Optional.of(skill));

        service = new SkillSuiteBundleMemberProgressService(
                operationRepository, memberRepository, actorContextService, namespaceRepository,
                skillRepository, versionRepository, reviewSubmitService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void publishedMemberCompletesAndOperationBecomesReadyForDraft() {
        Fixture fixture = fixture(SkillVersionStatus.PUBLISHED, SkillVisibility.PUBLIC);
        when(fixture.version().isDownloadReady()).thenReturn(true);

        assertThat(service.reconcile("operation"))
                .isEqualTo(SkillSuiteBundleMemberProgressService.ProgressOutcome.READY_FOR_DRAFT);

        assertThat(fixture.member().getStatus()).isEqualTo(SkillSuiteBundleMemberResultStatus.COMPLETED);
        assertThat(fixture.operation().getStatus()).isEqualTo(SkillSuiteBundleOperationStatus.RUNNING);
    }

    @Test
    void scanFailureBlocksTheSameBoundVersionForRetry() {
        Fixture fixture = fixture(SkillVersionStatus.SCAN_FAILED, SkillVisibility.PUBLIC);

        assertThat(service.reconcile("operation"))
                .isEqualTo(SkillSuiteBundleMemberProgressService.ProgressOutcome.TERMINAL);

        assertThat(fixture.member().getStatus())
                .isEqualTo(SkillSuiteBundleMemberResultStatus.BLOCKED_RETRYABLE);
        assertThat(fixture.member().getSkillVersionId()).isEqualTo(12L);
        assertThat(fixture.operation().getFailureCode()).isEqualTo("MEMBER_SCAN_FAILED");
    }

    @Test
    void privateUploadedMemberUsesExistingConfirmationBoundary() {
        Fixture fixture = fixture(SkillVersionStatus.UPLOADED, SkillVisibility.PRIVATE);

        assertThat(service.reconcile("operation"))
                .isEqualTo(SkillSuiteBundleMemberProgressService.ProgressOutcome.READY_FOR_DRAFT);

        verify(reviewSubmitService).confirmPublish(11L, 12L, "actor", Map.of(), Set.of());
        assertThat(fixture.member().getStatus()).isEqualTo(SkillSuiteBundleMemberResultStatus.COMPLETED);
    }

    @Test
    void pendingReviewKeepsTheDurableOperationWaiting() {
        Fixture fixture = fixture(SkillVersionStatus.PENDING_REVIEW, SkillVisibility.PUBLIC);

        assertThat(service.reconcile("operation"))
                .isEqualTo(SkillSuiteBundleMemberProgressService.ProgressOutcome.WAITING);

        assertThat(fixture.member().getStatus())
                .isEqualTo(SkillSuiteBundleMemberResultStatus.WAITING_FOR_MEMBER);
        assertThat(fixture.operation().getStatus())
                .isEqualTo(SkillSuiteBundleOperationStatus.WAITING_FOR_MEMBERS);
    }

    @Test
    void rejectedMemberRequiresAWholeNewPreview() {
        fixture(SkillVersionStatus.REJECTED, SkillVisibility.PUBLIC);

        assertThatThrownBy(() -> service.reconcile("operation"))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class)
                .extracting("messageCode")
                .isEqualTo("error.suite.bundle.member.stateChanged");
    }

    private Fixture fixture(SkillVersionStatus status, SkillVisibility visibility) {
        SkillSuiteBundleExecutionOperation operation = new SkillSuiteBundleExecutionOperation(
                "operation", "preview", "request", "actor", SkillSuiteBundleMode.CREATE,
                1L, "suite", null, null, "1.0.0", "archive", "a".repeat(64),
                Map.of(), "digest", NOW.minusSeconds(1));
        SkillSuiteBundleMemberResult member = new SkillSuiteBundleMemberResult(
                "operation", 0, new SkillSuiteBundleCoordinate("global", "member"),
                SkillSuiteBundleMemberSourceType.PACKAGE, "skills/member", visibility, "1.0.0",
                SkillSuiteBundleRelationshipChange.ADDED, SkillSuiteBundlePublishAction.CREATE_SKILL,
                "sha256:fingerprint", null, null, List.of(), List.of(), NOW.minusSeconds(1));
        member.start(NOW.minusMillis(500));
        member.bindVersion(11L, 12L, NOW.minusMillis(400));
        member.markWaiting(NOW.minusMillis(300));
        SkillVersion version = mock(SkillVersion.class);
        when(version.getId()).thenReturn(12L);
        when(version.getSkillId()).thenReturn(11L);
        when(version.getVersion()).thenReturn("1.0.0");
        when(version.getStatus()).thenReturn(status);
        when(versionRepository.findById(12L)).thenReturn(Optional.of(version));
        Skill skill = mock(Skill.class);
        when(skill.getId()).thenReturn(11L);
        when(skill.getNamespaceId()).thenReturn(1L);
        when(skill.getSlug()).thenReturn("member");
        when(skill.getOwnerId()).thenReturn("actor");
        when(skill.getStatus()).thenReturn(SkillStatus.ACTIVE);
        when(skill.getVisibility()).thenReturn(visibility);
        // Replace the default public mock for the requested visibility.
        when(skillRepository.findById(11L)).thenReturn(Optional.of(skill));
        when(operationRepository.findByIdForUpdate("operation")).thenReturn(Optional.of(operation));
        when(memberRepository.findByOperationIdOrderByPositionForUpdate("operation"))
                .thenReturn(List.of(member));
        return new Fixture(operation, member, version);
    }

    private record Fixture(
            SkillSuiteBundleExecutionOperation operation,
            SkillSuiteBundleMemberResult member,
            SkillVersion version
    ) {
    }
}
