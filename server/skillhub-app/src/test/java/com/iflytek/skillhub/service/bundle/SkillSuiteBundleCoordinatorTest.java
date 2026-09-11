package com.iflytek.skillhub.service.bundle;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillSuiteBundleCoordinatorTest {

    private SkillSuiteBundleMemberExecutionService executionService;
    private SkillSuiteBundleMemberProgressService progressService;
    private SkillSuiteBundleDraftCreationService draftCreationService;
    private SkillSuiteBundleOperationStateService stateService;
    private SkillSuiteBundleCoordinator coordinator;

    @BeforeEach
    void setUp() {
        executionService = mock(SkillSuiteBundleMemberExecutionService.class);
        progressService = mock(SkillSuiteBundleMemberProgressService.class);
        draftCreationService = mock(SkillSuiteBundleDraftCreationService.class);
        stateService = mock(SkillSuiteBundleOperationStateService.class);
        coordinator = new SkillSuiteBundleCoordinator(
                executionService, progressService, draftCreationService, stateService);
    }

    @Test
    void createsDraftOnlyAfterAllMemberWorkConverges() {
        when(executionService.executeNext("operation"))
                .thenReturn(SkillSuiteBundleMemberExecutionService.ExecutionOutcome.PROGRESSED)
                .thenReturn(SkillSuiteBundleMemberExecutionService.ExecutionOutcome.NONE);
        when(progressService.reconcile("operation"))
                .thenReturn(SkillSuiteBundleMemberProgressService.ProgressOutcome.READY_FOR_DRAFT);

        coordinator.advance("operation");

        verify(executionService, org.mockito.Mockito.times(2)).executeNext("operation");
        verify(draftCreationService).create("operation");
        verify(stateService, never()).markBlockedRetryable(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void domainStateDriftRequiresFreshPreviewAndDoesNotCreateDraft() {
        when(executionService.executeNext("operation"))
                .thenThrow(new DomainBadRequestException("error.suite.bundle.member.stateChanged"));

        coordinator.advance("operation");

        verify(stateService).markRepreviewRequired("operation", "BUNDLE_PLAN_CHANGED");
        verify(draftCreationService, never()).create("operation");
    }

    @Test
    void infrastructureFailureIsRetryableAndDoesNotCreateDraft() {
        doThrow(new IllegalStateException("storage unavailable"))
                .when(executionService).executeNext("operation");

        coordinator.advance("operation");

        verify(stateService).markBlockedRetryable(
                "operation", "MEMBER_EXECUTION_FAILED", "IllegalStateException");
        verify(draftCreationService, never()).create("operation");
    }
}
