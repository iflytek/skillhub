package com.iflytek.skillhub.task;

import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperationRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleOperationStatus;
import com.iflytek.skillhub.service.bundle.SkillSuiteBundleCoordinator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Bounded recovery for lost, delayed, or out-of-order Bundle lifecycle events. */
@Component
public class SkillSuiteBundleRecoveryTask {

    private static final Set<SkillSuiteBundleOperationStatus> RECOVERABLE = Set.of(
            SkillSuiteBundleOperationStatus.RUNNING,
            SkillSuiteBundleOperationStatus.WAITING_FOR_MEMBERS);

    private final SkillSuiteBundleExecutionOperationRepository operationRepository;
    private final SkillSuiteBundleCoordinator coordinator;

    public SkillSuiteBundleRecoveryTask(
            SkillSuiteBundleExecutionOperationRepository operationRepository,
            SkillSuiteBundleCoordinator coordinator
    ) {
        this.operationRepository = operationRepository;
        this.coordinator = coordinator;
    }

    @Scheduled(fixedDelayString = "${skillhub.suite.bundle.recovery-interval-ms:5000}")
    public void recover() {
        operationRepository.findTop100ByStatusInOrderByUpdatedAtAsc(RECOVERABLE).stream()
                .map(operation -> operation.getOperationId())
                .forEach(coordinator::advance);
    }
}
