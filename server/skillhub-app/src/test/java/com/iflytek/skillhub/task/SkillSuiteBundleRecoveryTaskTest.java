package com.iflytek.skillhub.task;

import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperation;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperationRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleOperationStatus;
import com.iflytek.skillhub.service.bundle.SkillSuiteBundleCoordinator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillSuiteBundleRecoveryTaskTest {

    @Test
    void advancesOnlyTheBoundedRecoverableQueryResults() {
        SkillSuiteBundleExecutionOperationRepository repository =
                mock(SkillSuiteBundleExecutionOperationRepository.class);
        SkillSuiteBundleCoordinator coordinator = mock(SkillSuiteBundleCoordinator.class);
        SkillSuiteBundleExecutionOperation first = mock(SkillSuiteBundleExecutionOperation.class);
        SkillSuiteBundleExecutionOperation second = mock(SkillSuiteBundleExecutionOperation.class);
        when(first.getOperationId()).thenReturn("operation-a");
        when(second.getOperationId()).thenReturn("operation-b");
        when(repository.findTop100ByStatusInOrderByUpdatedAtAsc(Set.of(
                SkillSuiteBundleOperationStatus.RUNNING,
                SkillSuiteBundleOperationStatus.WAITING_FOR_MEMBERS)))
                .thenReturn(List.of(first, second));

        new SkillSuiteBundleRecoveryTask(repository, coordinator).recover();

        verify(coordinator).advance("operation-a");
        verify(coordinator).advance("operation-b");
    }
}
