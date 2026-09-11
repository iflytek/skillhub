package com.iflytek.skillhub.service.bundle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperation;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperationRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleOperationStatus;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSession;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSessionRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewStatus;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Deletes only Bundle staging keys while retaining retryable cleanup evidence on failure. */
@Service
public class SkillSuiteBundleStagedCleanupService {

    private static final Logger log = LoggerFactory.getLogger(SkillSuiteBundleStagedCleanupService.class);
    private static final String STAGING_PREFIX = "temporary/suite-bundles/";

    private final SkillSuiteBundlePreviewSessionRepository previewRepository;
    private final SkillSuiteBundleExecutionOperationRepository operationRepository;
    private final ObjectStorageService objectStorageService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SkillSuiteBundleStagedCleanupService(
            SkillSuiteBundlePreviewSessionRepository previewRepository,
            SkillSuiteBundleExecutionOperationRepository operationRepository,
            ObjectStorageService objectStorageService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.previewRepository = previewRepository;
        this.operationRepository = operationRepository;
        this.objectStorageService = objectStorageService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public int expireReadyPreviews() {
        return previewRepository.expireReadyBefore(clock.instant());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cleanupExpiredPreview(String previewToken) {
        SkillSuiteBundlePreviewSession preview = previewRepository.findByIdForUpdate(previewToken).orElse(null);
        if (preview == null || preview.getStatus() != SkillSuiteBundlePreviewStatus.EXPIRED
                || preview.getStagedObjectsCleanedAt() != null
                || operationRepository.findByPreviewToken(previewToken).isPresent()) {
            return;
        }
        Instant now = clock.instant();
        CleanupKeys keys = cleanupKeys(preview.getArchiveObjectKey(), preview.getPlan());
        if (!keys.valid()) {
            preview.markStagedCleanupFailed("STAGED_KEY_SCOPE_INVALID", now);
            previewRepository.save(preview);
            previewRepository.flush();
            return;
        }
        try {
            objectStorageService.deleteObjects(keys.values());
            preview.markStagedObjectsCleaned(now);
            previewRepository.delete(preview);
            previewRepository.flush();
        } catch (RuntimeException exception) {
            preview.markStagedCleanupFailed("STORAGE_DELETE_FAILED", now);
            previewRepository.save(preview);
            previewRepository.flush();
            log.warn("Failed to clean expired Suite Bundle preview staging [previewToken={}]",
                    previewToken, exception);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cleanupTerminalOperation(String operationId) {
        SkillSuiteBundleExecutionOperation operation = operationRepository.findByIdForUpdate(operationId)
                .orElse(null);
        if (operation == null || !terminal(operation.getStatus())
                || operation.getStagedObjectsCleanedAt() != null) {
            return;
        }
        Instant now = clock.instant();
        CleanupKeys keys = cleanupKeys(operation.getArchiveObjectKey(), operation.getPlan());
        if (!keys.valid()) {
            operation.markStagedCleanupFailed("STAGED_KEY_SCOPE_INVALID", now);
            operationRepository.save(operation);
            operationRepository.flush();
            return;
        }
        try {
            objectStorageService.deleteObjects(keys.values());
            operation.markStagedObjectsCleaned(now);
            operationRepository.save(operation);
            operationRepository.flush();
        } catch (RuntimeException exception) {
            operation.markStagedCleanupFailed("STORAGE_DELETE_FAILED", now);
            operationRepository.save(operation);
            operationRepository.flush();
            log.warn("Failed to clean terminal Suite Bundle operation staging [operationId={}]",
                    operationId, exception);
        }
    }

    private CleanupKeys cleanupKeys(String archiveObjectKey, java.util.Map<String, Object> planJson) {
        try {
            SkillSuiteBundlePreviewPlanner.PreviewPlan plan = objectMapper.convertValue(
                    planJson, SkillSuiteBundlePreviewPlanner.PreviewPlan.class);
            Set<String> keys = new LinkedHashSet<>();
            keys.add(archiveObjectKey);
            plan.members().stream()
                    .flatMap(member -> member.files().stream())
                    .map(SkillSuiteBundlePackageAnalyzer.StagedMemberFile::objectKey)
                    .forEach(keys::add);
            List<String> values = List.copyOf(keys);
            return new CleanupKeys(
                    !values.isEmpty() && values.stream().allMatch(this::inStagingScope), values);
        } catch (RuntimeException exception) {
            return new CleanupKeys(false, List.of());
        }
    }

    private boolean inStagingScope(String key) {
        return key != null && key.startsWith(STAGING_PREFIX) && key.length() > STAGING_PREFIX.length();
    }

    private boolean terminal(SkillSuiteBundleOperationStatus status) {
        return status == SkillSuiteBundleOperationStatus.REPREVIEW_REQUIRED
                || status == SkillSuiteBundleOperationStatus.SUITE_DRAFT_CREATED
                || status == SkillSuiteBundleOperationStatus.CANCELLED;
    }

    private record CleanupKeys(boolean valid, List<String> values) {
    }
}
