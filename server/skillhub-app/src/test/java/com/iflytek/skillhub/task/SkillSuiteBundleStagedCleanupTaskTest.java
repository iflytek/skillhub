package com.iflytek.skillhub.task;

import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperation;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperationRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleOperationStatus;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSession;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSessionRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewStatus;
import com.iflytek.skillhub.service.bundle.SkillSuiteBundleStagedCleanupService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillSuiteBundleStagedCleanupTaskTest {

    @Test
    void expiresPreviewsAndProcessesOnlyBoundedCleanupCandidates() {
        SkillSuiteBundlePreviewSessionRepository previews = mock(SkillSuiteBundlePreviewSessionRepository.class);
        SkillSuiteBundleExecutionOperationRepository operations =
                mock(SkillSuiteBundleExecutionOperationRepository.class);
        SkillSuiteBundleStagedCleanupService service = mock(SkillSuiteBundleStagedCleanupService.class);
        SkillSuiteBundlePreviewSession preview = mock(SkillSuiteBundlePreviewSession.class);
        SkillSuiteBundleExecutionOperation operation = mock(SkillSuiteBundleExecutionOperation.class);
        when(preview.getToken()).thenReturn("preview");
        when(operation.getOperationId()).thenReturn("operation");
        when(previews.findTop100ByStatusAndStagedObjectsCleanedAtIsNullOrderByExpiresAtAsc(
                SkillSuiteBundlePreviewStatus.EXPIRED)).thenReturn(List.of(preview));
        Set<SkillSuiteBundleOperationStatus> terminal = Set.of(
                SkillSuiteBundleOperationStatus.REPREVIEW_REQUIRED,
                SkillSuiteBundleOperationStatus.SUITE_DRAFT_CREATED,
                SkillSuiteBundleOperationStatus.CANCELLED);
        when(operations.findTop100ByStatusInAndStagedObjectsCleanedAtIsNullOrderByCompletedAtAsc(terminal))
                .thenReturn(List.of(operation));

        new SkillSuiteBundleStagedCleanupTask(previews, operations, service).cleanup();

        verify(service).expireReadyPreviews();
        verify(service).cleanupExpiredPreview("preview");
        verify(service).cleanupTerminalOperation("operation");
    }
}
