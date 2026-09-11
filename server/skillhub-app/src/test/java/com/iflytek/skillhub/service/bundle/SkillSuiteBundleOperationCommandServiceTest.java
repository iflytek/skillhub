package com.iflytek.skillhub.service.bundle;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleCoordinate;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperation;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperationRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResult;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultStatus;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberSourceType;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMode;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillSuiteBundleOperationCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-11T08:00:00Z");

    private SkillSuiteBundleExecutionOperationRepository operationRepository;
    private SkillSuiteBundleMemberResultRepository memberRepository;
    private SkillSuiteBundlePreviewSessionRepository previewRepository;
    private SkillSuiteBundlePreviewRevalidationService revalidationService;
    private SkillSuiteBundleOperationCommandService service;

    @BeforeEach
    void setUp() {
        operationRepository = mock(SkillSuiteBundleExecutionOperationRepository.class);
        memberRepository = mock(SkillSuiteBundleMemberResultRepository.class);
        previewRepository = mock(SkillSuiteBundlePreviewSessionRepository.class);
        revalidationService = mock(SkillSuiteBundlePreviewRevalidationService.class);
        service = new SkillSuiteBundleOperationCommandService(
                operationRepository, memberRepository, previewRepository, revalidationService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void originalActorCancelsOperationAndOnlyUnfinishedMembers() {
        SkillSuiteBundleExecutionOperation operation = operation();
        SkillSuiteBundleMemberResult planned = member("planned");
        SkillSuiteBundleMemberResult completed = member("completed");
        setStatus(completed, SkillSuiteBundleMemberResultStatus.COMPLETED);
        when(operationRepository.findByIdForUpdate("operation-1")).thenReturn(Optional.of(operation));
        when(memberRepository.findByOperationIdOrderByPositionForUpdate("operation-1"))
                .thenReturn(List.of(planned, completed));

        var response = service.cancel("operation-1", "actor", Map.of(), Set.of());

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.replayed()).isFalse();
        assertThat(operation.isReservationActive()).isFalse();
        assertThat(planned.getStatus()).isEqualTo(SkillSuiteBundleMemberResultStatus.CANCELLED);
        assertThat(completed.getStatus()).isEqualTo(SkillSuiteBundleMemberResultStatus.COMPLETED);
        verify(operationRepository).flush();
        verify(memberRepository).saveAll(List.of(planned, completed));
    }

    @Test
    void repeatedCancelIsIdempotentAndUnrelatedCallerSeesNotFound() {
        SkillSuiteBundleExecutionOperation operation = operation();
        operation.cancel(NOW.minusSeconds(1));
        when(operationRepository.findByIdForUpdate("operation-1")).thenReturn(Optional.of(operation));

        var replay = service.cancel("operation-1", "actor", Map.of(), Set.of());
        assertThat(replay.replayed()).isTrue();
        verify(memberRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());

        assertThatThrownBy(() -> service.cancel(
                "operation-1", "unrelated", Map.of(), Set.of()))
                .isInstanceOf(DomainNotFoundException.class)
                .extracting("messageCode")
                .isEqualTo("error.suite.bundle.operation.notFound");
    }

    @Test
    void namespaceAdminMayCancelAnOperationForGovernance() {
        when(operationRepository.findByIdForUpdate("operation-1"))
                .thenReturn(Optional.of(operation()));
        when(memberRepository.findByOperationIdOrderByPositionForUpdate("operation-1"))
                .thenReturn(List.of());

        assertThat(service.cancel(
                "operation-1", "admin", Map.of(1L, NamespaceRole.ADMIN), Set.of()).status())
                .isEqualTo("CANCELLED");
    }

    @Test
    void retriesOnlyUnfinishedWorkOnTheOriginalOperationId() {
        SkillSuiteBundleExecutionOperation operation = operation();
        operation.markBlockedRetryable("TEMPORARY", "try later", NOW.minusSeconds(1));
        SkillSuiteBundleMemberResult blocked = member("blocked");
        setStatus(blocked, SkillSuiteBundleMemberResultStatus.BLOCKED_RETRYABLE);
        SkillSuiteBundleMemberResult completed = member("completed");
        setStatus(completed, SkillSuiteBundleMemberResultStatus.COMPLETED);
        SkillSuiteBundlePreviewSession preview = mock(SkillSuiteBundlePreviewSession.class);
        when(operationRepository.findByIdForUpdate("operation-1")).thenReturn(Optional.of(operation));
        when(previewRepository.findById("preview-1")).thenReturn(Optional.of(preview));
        when(memberRepository.findByOperationIdOrderByPositionForUpdate("operation-1"))
                .thenReturn(List.of(blocked, completed));

        var response = service.retry("operation-1", "actor", Map.of(), Set.of());

        assertThat(response.operationId()).isEqualTo("operation-1");
        assertThat(response.status()).isEqualTo("RUNNING");
        assertThat(operation.isReservationActive()).isTrue();
        assertThat(operation.getFailureCode()).isNull();
        assertThat(blocked.getStatus()).isEqualTo(SkillSuiteBundleMemberResultStatus.PLANNED);
        assertThat(completed.getStatus()).isEqualTo(SkillSuiteBundleMemberResultStatus.COMPLETED);
        verify(revalidationService).requireUnchanged(preview, "actor", Map.of(), Set.of());
    }

    @Test
    void retryMovesToRepreviewRequiredWhenTheBoundPlanChanged() {
        SkillSuiteBundleExecutionOperation operation = operation();
        operation.markBlockedRetryable("TEMPORARY", null, NOW.minusSeconds(1));
        SkillSuiteBundleMemberResult blocked = member("blocked");
        setStatus(blocked, SkillSuiteBundleMemberResultStatus.BLOCKED_RETRYABLE);
        SkillSuiteBundlePreviewSession preview = mock(SkillSuiteBundlePreviewSession.class);
        when(operationRepository.findByIdForUpdate("operation-1")).thenReturn(Optional.of(operation));
        when(previewRepository.findById("preview-1")).thenReturn(Optional.of(preview));
        when(memberRepository.findByOperationIdOrderByPositionForUpdate("operation-1"))
                .thenReturn(List.of(blocked));
        when(revalidationService.requireUnchanged(preview, "actor", Map.of(), Set.of()))
                .thenThrow(new DomainBadRequestException("error.suite.bundle.preview.stateChanged"));

        var response = service.retry("operation-1", "actor", Map.of(), Set.of());

        assertThat(response.status()).isEqualTo("REPREVIEW_REQUIRED");
        assertThat(operation.isReservationActive()).isFalse();
        assertThat(blocked.getStatus()).isEqualTo(SkillSuiteBundleMemberResultStatus.REPREVIEW_REQUIRED);
    }

    @Test
    void retryRejectsNonRetryableOrUnrelatedOperations() {
        when(operationRepository.findByIdForUpdate("operation-1")).thenReturn(Optional.of(operation()));

        assertThatThrownBy(() -> service.retry("operation-1", "actor", Map.of(), Set.of()))
                .isInstanceOf(DomainBadRequestException.class)
                .extracting("messageCode")
                .isEqualTo("error.suite.bundle.operation.retry.notAllowed");

        assertThatThrownBy(() -> service.retry("operation-1", "unrelated", Map.of(), Set.of()))
                .isInstanceOf(DomainNotFoundException.class)
                .extracting("messageCode")
                .isEqualTo("error.suite.bundle.operation.notFound");
    }

    private SkillSuiteBundleExecutionOperation operation() {
        return new SkillSuiteBundleExecutionOperation(
                "operation-1", "preview-1", "request-1", "actor", SkillSuiteBundleMode.CREATE,
                1L, "suite", null, null, "1.0.0", "archive.zip", "a".repeat(64),
                Map.of("plan", "value"), "warning-digest", NOW.minusSeconds(60));
    }

    private SkillSuiteBundleMemberResult member(String slug) {
        return new SkillSuiteBundleMemberResult(
                "operation-1", 0, new SkillSuiteBundleCoordinate("global", slug),
                SkillSuiteBundleMemberSourceType.PACKAGE, "skills/" + slug, SkillVisibility.PUBLIC,
                "1.0.0", SkillSuiteBundleRelationshipChange.ADDED,
                SkillSuiteBundlePublishAction.CREATE_SKILL, "sha256:" + slug, null, null,
                List.of(), List.of(), NOW.minusSeconds(60));
    }

    private void setStatus(
            SkillSuiteBundleMemberResult member,
            SkillSuiteBundleMemberResultStatus status
    ) {
        org.springframework.test.util.ReflectionTestUtils.setField(member, "status", status);
    }
}
