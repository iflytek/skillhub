package com.iflytek.skillhub.service.bundle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleCoordinate;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperation;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperationRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberSourceType;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMode;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSession;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSessionRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePublishAction;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleRelationshipChange;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillSuiteBundleStagedCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-11T08:00:00Z");
    private SkillSuiteBundlePreviewSessionRepository previewRepository;
    private SkillSuiteBundleExecutionOperationRepository operationRepository;
    private ObjectStorageService storage;
    private ObjectMapper objectMapper;
    private SkillSuiteBundleStagedCleanupService service;

    @BeforeEach
    void setUp() {
        previewRepository = mock(SkillSuiteBundlePreviewSessionRepository.class);
        operationRepository = mock(SkillSuiteBundleExecutionOperationRepository.class);
        storage = mock(ObjectStorageService.class);
        objectMapper = mock(ObjectMapper.class);
        service = new SkillSuiteBundleStagedCleanupService(
                previewRepository, operationRepository, storage, objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void deletesOnlyBoundStagingKeysThenRemovesExpiredPreviewRecord() {
        SkillSuiteBundlePreviewSession preview = preview();
        preview.markExpired();
        when(previewRepository.findByIdForUpdate("preview")).thenReturn(Optional.of(preview));
        when(operationRepository.findByPreviewToken("preview")).thenReturn(Optional.empty());
        when(objectMapper.convertValue(preview.getPlan(), SkillSuiteBundlePreviewPlanner.PreviewPlan.class))
                .thenReturn(plan());

        service.cleanupExpiredPreview("preview");

        verify(storage).deleteObjects(List.of(
                "temporary/suite-bundles/staging/bundle.zip",
                "temporary/suite-bundles/staging/skills/member/SKILL.md"));
        verify(previewRepository).delete(preview);
        assertThat(preview.getStagedObjectsCleanedAt()).isEqualTo(NOW);
    }

    @Test
    void storageFailureRetainsRetryableEvidenceAndPreviewRow() {
        SkillSuiteBundlePreviewSession preview = preview();
        preview.markExpired();
        when(previewRepository.findByIdForUpdate("preview")).thenReturn(Optional.of(preview));
        when(operationRepository.findByPreviewToken("preview")).thenReturn(Optional.empty());
        when(objectMapper.convertValue(preview.getPlan(), SkillSuiteBundlePreviewPlanner.PreviewPlan.class))
                .thenReturn(plan());
        doThrow(new IllegalStateException("storage unavailable")).when(storage)
                .deleteObjects(org.mockito.ArgumentMatchers.anyList());

        service.cleanupExpiredPreview("preview");

        assertThat(preview.getStagedCleanupFailureCode()).isEqualTo("STORAGE_DELETE_FAILED");
        assertThat(preview.getStagedCleanupFailedAt()).isEqualTo(NOW);
        assertThat(preview.getStagedObjectsCleanedAt()).isNull();
        verify(previewRepository, never()).delete(preview);
        verify(previewRepository).save(preview);
    }

    @Test
    void activeOperationIsNeverCleanedEvenWhenItsOriginalPreviewTtlPassed() {
        SkillSuiteBundleExecutionOperation operation = operation();
        when(operationRepository.findByIdForUpdate("operation")).thenReturn(Optional.of(operation));

        service.cleanupTerminalOperation("operation");

        verify(storage, never()).deleteObjects(org.mockito.ArgumentMatchers.anyList());
        assertThat(operation.getStagedObjectsCleanedAt()).isNull();
    }

    @Test
    void terminalOperationCleansStagingWithoutDeletingPublishedMemberRecords() {
        SkillSuiteBundleExecutionOperation operation = operation();
        operation.cancel(NOW.minusSeconds(1));
        when(operationRepository.findByIdForUpdate("operation")).thenReturn(Optional.of(operation));
        when(objectMapper.convertValue(operation.getPlan(), SkillSuiteBundlePreviewPlanner.PreviewPlan.class))
                .thenReturn(plan());

        service.cleanupTerminalOperation("operation");

        verify(storage).deleteObjects(List.of(
                "temporary/suite-bundles/staging/bundle.zip",
                "temporary/suite-bundles/staging/skills/member/SKILL.md"));
        assertThat(operation.getStagedObjectsCleanedAt()).isEqualTo(NOW);
        verify(operationRepository).save(operation);
    }

    @Test
    void refusesToDeleteAnyKeyOutsideTheDedicatedStagingPrefix() {
        SkillSuiteBundleExecutionOperation operation = operation();
        operation.cancel(NOW.minusSeconds(1));
        org.springframework.test.util.ReflectionTestUtils.setField(
                operation, "archiveObjectKey", "packages/1/2/bundle.zip");
        when(operationRepository.findByIdForUpdate("operation")).thenReturn(Optional.of(operation));
        when(objectMapper.convertValue(operation.getPlan(), SkillSuiteBundlePreviewPlanner.PreviewPlan.class))
                .thenReturn(plan());

        service.cleanupTerminalOperation("operation");

        verify(storage, never()).deleteObjects(org.mockito.ArgumentMatchers.anyList());
        assertThat(operation.getStagedCleanupFailureCode()).isEqualTo("STAGED_KEY_SCOPE_INVALID");
    }

    private SkillSuiteBundlePreviewSession preview() {
        return new SkillSuiteBundlePreviewSession(
                "preview", "actor", SkillSuiteBundleMode.CREATE, 1L, "suite", null, null,
                "1.0.0", "temporary/suite-bundles/staging/bundle.zip", "a".repeat(64),
                Map.of("manifest", "value"), Map.of("plan", "value"), "digest",
                NOW.minusSeconds(1), NOW.minusSeconds(60));
    }

    private SkillSuiteBundleExecutionOperation operation() {
        return new SkillSuiteBundleExecutionOperation(
                "operation", "preview", "request", "actor", SkillSuiteBundleMode.CREATE,
                1L, "suite", null, null, "1.0.0",
                "temporary/suite-bundles/staging/bundle.zip", "a".repeat(64),
                Map.of("plan", "value"), "digest", NOW.minusSeconds(60));
    }

    private SkillSuiteBundlePreviewPlanner.PreviewPlan plan() {
        var file = new SkillSuiteBundlePackageAnalyzer.StagedMemberFile(
                "SKILL.md", 12, "text/markdown", "b".repeat(64),
                "temporary/suite-bundles/staging/skills/member/SKILL.md");
        var member = new SkillSuiteBundlePreviewPlanner.MemberPlan(
                new SkillSuiteBundleCoordinate("global", "member"),
                SkillSuiteBundleMemberSourceType.PACKAGE,
                SkillSuiteBundleRelationshipChange.ADDED,
                SkillSuiteBundlePublishAction.CREATE_SKILL,
                null, null, SkillVisibility.PRIVATE, "1.0.0", "sha256:fingerprint",
                List.of(file), List.of(), List.of());
        return new SkillSuiteBundlePreviewPlanner.PreviewPlan(
                SkillSuiteBundleMode.CREATE, new SkillSuiteBundleCoordinate("global", "suite"),
                1L, null, null, "1.0.0", "Suite", "Summary", "Overview",
                SkillVisibility.PRIVATE, List.of(member), List.of(), List.of(), List.of(), "digest");
    }
}
