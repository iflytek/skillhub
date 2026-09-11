package com.iflytek.skillhub.task;

import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperationRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleOperationStatus;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSessionRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewStatus;
import com.iflytek.skillhub.service.bundle.SkillSuiteBundleStagedCleanupService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Bounded cleanup of expired previews and terminal Bundle staging objects. */
@Component
public class SkillSuiteBundleStagedCleanupTask {

    private static final Set<SkillSuiteBundleOperationStatus> TERMINAL = Set.of(
            SkillSuiteBundleOperationStatus.REPREVIEW_REQUIRED,
            SkillSuiteBundleOperationStatus.SUITE_DRAFT_CREATED,
            SkillSuiteBundleOperationStatus.CANCELLED);

    private final SkillSuiteBundlePreviewSessionRepository previewRepository;
    private final SkillSuiteBundleExecutionOperationRepository operationRepository;
    private final SkillSuiteBundleStagedCleanupService cleanupService;

    public SkillSuiteBundleStagedCleanupTask(
            SkillSuiteBundlePreviewSessionRepository previewRepository,
            SkillSuiteBundleExecutionOperationRepository operationRepository,
            SkillSuiteBundleStagedCleanupService cleanupService
    ) {
        this.previewRepository = previewRepository;
        this.operationRepository = operationRepository;
        this.cleanupService = cleanupService;
    }

    @Scheduled(fixedDelayString = "${skillhub.suite.bundle.cleanup-interval-ms:60000}")
    public void cleanup() {
        cleanupService.expireReadyPreviews();
        previewRepository
                .findTop100ByStatusAndStagedObjectsCleanedAtIsNullOrderByExpiresAtAsc(
                        SkillSuiteBundlePreviewStatus.EXPIRED)
                .stream()
                .map(preview -> preview.getToken())
                .forEach(cleanupService::cleanupExpiredPreview);
        operationRepository
                .findTop100ByStatusInAndStagedObjectsCleanedAtIsNullOrderByCompletedAtAsc(TERMINAL)
                .stream()
                .map(operation -> operation.getOperationId())
                .forEach(cleanupService::cleanupTerminalOperation);
    }
}
