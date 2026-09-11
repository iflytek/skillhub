package com.iflytek.skillhub.service.bundle;

import com.iflytek.skillhub.domain.shared.exception.LocalizedDomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Advances one durable Bundle operation through bounded, independently committed steps. */
@Service
public class SkillSuiteBundleCoordinator {

    private static final Logger log = LoggerFactory.getLogger(SkillSuiteBundleCoordinator.class);
    private static final int MAX_MEMBER_STEPS = 100;

    private final SkillSuiteBundleMemberExecutionService executionService;
    private final SkillSuiteBundleMemberProgressService progressService;
    private final SkillSuiteBundleDraftCreationService draftCreationService;
    private final SkillSuiteBundleOperationStateService stateService;

    public SkillSuiteBundleCoordinator(
            SkillSuiteBundleMemberExecutionService executionService,
            SkillSuiteBundleMemberProgressService progressService,
            SkillSuiteBundleDraftCreationService draftCreationService,
            SkillSuiteBundleOperationStateService stateService
    ) {
        this.executionService = executionService;
        this.progressService = progressService;
        this.draftCreationService = draftCreationService;
        this.stateService = stateService;
    }

    public void advance(String operationId) {
        try {
            for (int step = 0; step < MAX_MEMBER_STEPS; step++) {
                if (executionService.executeNext(operationId)
                        == SkillSuiteBundleMemberExecutionService.ExecutionOutcome.NONE) {
                    break;
                }
            }
            SkillSuiteBundleMemberProgressService.ProgressOutcome outcome =
                    progressService.reconcile(operationId);
            if (outcome == SkillSuiteBundleMemberProgressService.ProgressOutcome.READY_FOR_DRAFT) {
                draftCreationService.create(operationId);
            }
        } catch (LocalizedDomainException exception) {
            stateService.markRepreviewRequired(operationId, "BUNDLE_PLAN_CHANGED");
            log.info("Suite Bundle requires a new preview [operationId={}, reason={}]",
                    operationId, exception.messageCode());
        } catch (RuntimeException exception) {
            stateService.markBlockedRetryable(
                    operationId, "MEMBER_EXECUTION_FAILED", exception.getClass().getSimpleName());
            log.error("Suite Bundle execution blocked [operationId={}]", operationId, exception);
        }
    }
}
