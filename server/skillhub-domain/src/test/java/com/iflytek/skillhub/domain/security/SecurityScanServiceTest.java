package com.iflytek.skillhub.domain.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SecurityScanServiceTest {

    @Mock
    private SecurityAuditRepository auditRepository;

    @Mock
    private SkillVersionRepository skillVersionRepository;

    @Mock
    private ScanTaskProducer scanTaskProducer;

    private SecurityScanService service;

    @BeforeEach
    void setUp() {
        service = new SecurityScanService(
                auditRepository,
                skillVersionRepository,
                scanTaskProducer,
                new ObjectMapper(),
                "local",
                true
        );
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void securityAudit_startsWithSuspiciousUnsafeDefaults() {
        SecurityAudit audit = new SecurityAudit(42L, ScannerType.SKILL_SCANNER);

        assertThat(audit.getSkillVersionId()).isEqualTo(42L);
        assertThat(audit.getScannerType()).isEqualTo(ScannerType.SKILL_SCANNER);
        assertThat(audit.getVerdict()).isEqualTo(SecurityVerdict.SUSPICIOUS);
        assertThat(audit.getIsSafe()).isFalse();
        assertThat(audit.getFindingsCount()).isZero();
        assertThat(audit.getFindings()).isEqualTo("[]");
    }

    @Test
    void triggerScan_createsInitialAuditPublishesTaskAndMovesVersionToScanning() throws Exception {
        SkillVersion version = new SkillVersion(8L, "1.0.0", "publisher-1");
        setId(version, 42L);
        PackageEntry entry = new PackageEntry(
                "README.md",
                "# demo".getBytes(),
                6L,
                "text/markdown"
        );

        given(skillVersionRepository.findById(42L)).willReturn(Optional.of(version));

        service.triggerScan(42L, List.of(entry), "publisher-1");

        ArgumentCaptor<SecurityAudit> auditCaptor = ArgumentCaptor.forClass(SecurityAudit.class);
        ArgumentCaptor<ScanTask> taskCaptor = ArgumentCaptor.forClass(ScanTask.class);
        verify(auditRepository).save(auditCaptor.capture());
        verify(scanTaskProducer).publishScanTask(taskCaptor.capture());
        verify(skillVersionRepository).save(version);

        SecurityAudit audit = auditCaptor.getValue();
        ScanTask task = taskCaptor.getValue();
        assertThat(audit.getSkillVersionId()).isEqualTo(42L);
        assertThat(audit.getScannerType()).isEqualTo(ScannerType.SKILL_SCANNER);
        assertThat(version.getStatus()).isEqualTo(SkillVersionStatus.SCANNING);
        assertThat(task.versionId()).isEqualTo(42L);
        assertThat(task.publisherId()).isEqualTo("publisher-1");
        assertThat(task.skillPath()).contains("42");
        assertThat(task.bundleKey()).isNull();
    }

    @Test
    void triggerScan_uploadModePublishesBundleKeyWithoutLocalTempPath() throws Exception {
        service = new SecurityScanService(
                auditRepository,
                skillVersionRepository,
                scanTaskProducer,
                new ObjectMapper(),
                "upload",
                true
        );
        SkillVersion version = new SkillVersion(8L, "1.0.0", "publisher-1");
        setId(version, 42L);
        PackageEntry entry = new PackageEntry(
                "README.md",
                "# demo".getBytes(),
                6L,
                "text/markdown"
        );

        given(skillVersionRepository.findById(42L)).willReturn(Optional.of(version));

        service.triggerScan(42L, List.of(entry), "publisher-1");

        ArgumentCaptor<ScanTask> taskCaptor = ArgumentCaptor.forClass(ScanTask.class);
        verify(scanTaskProducer).publishScanTask(taskCaptor.capture());

        ScanTask task = taskCaptor.getValue();
        assertThat(task.versionId()).isEqualTo(42L);
        assertThat(task.skillPath()).isNull();
        assertThat(task.bundleKey()).isEqualTo("packages/8/42/bundle.zip");
    }

    @Test
    void retryStoredBundleScan_createsFreshAuditAndDurableOutbox() throws Exception {
        ScanTaskOutboxRepository outboxRepository = org.mockito.Mockito.mock(ScanTaskOutboxRepository.class);
        service = new SecurityScanService(
                auditRepository,
                skillVersionRepository,
                scanTaskProducer,
                new ObjectMapper(),
                "local",
                true,
                outboxRepository
        );
        SkillVersion version = new SkillVersion(8L, "1.0.0", "owner-1");
        setId(version, 42L);
        version.setStatus(SkillVersionStatus.SCAN_FAILED);

        ScanTask task = service.retryStoredBundleScan(version, "packages/8/42/bundle.zip", "owner-1");

        ArgumentCaptor<SecurityAudit> auditCaptor = ArgumentCaptor.forClass(SecurityAudit.class);
        ArgumentCaptor<ScanTaskOutbox> outboxCaptor = ArgumentCaptor.forClass(ScanTaskOutbox.class);
        verify(auditRepository).save(auditCaptor.capture());
        verify(outboxRepository).save(outboxCaptor.capture());
        verify(scanTaskProducer, never()).publishScanTask(any());
        verify(skillVersionRepository).save(version);
        assertThat(auditCaptor.getValue().getTaskId()).isEqualTo(task.taskId());
        assertThat(outboxCaptor.getValue().toScanTask()).isEqualTo(task);
        assertThat(task.bundleKey()).isEqualTo("packages/8/42/bundle.zip");
        assertThat(task.metadata()).containsEntry("scannerType", "skill-scanner");
        assertThat(version.getStatus()).isEqualTo(SkillVersionStatus.SCANNING);
    }

    @Test
    void retryStoredBundleScan_rejectsNonFailedVersion() throws Exception {
        SkillVersion version = new SkillVersion(8L, "1.0.0", "owner-1");
        setId(version, 42L);
        version.setStatus(SkillVersionStatus.SCANNING);

        assertThatThrownBy(() -> service.retryStoredBundleScan(version, "bundle.zip", "owner-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SCAN_FAILED");

        verify(auditRepository, never()).save(any());
    }

    @Test
    void triggerScan_defersTaskPublishingUntilTransactionCommit() throws Exception {
        SkillVersion version = new SkillVersion(8L, "1.0.0", "publisher-1");
        setId(version, 42L);
        PackageEntry entry = new PackageEntry(
                "README.md",
                "# demo".getBytes(),
                6L,
                "text/markdown"
        );

        given(skillVersionRepository.findById(42L)).willReturn(Optional.of(version));
        TransactionSynchronizationManager.initSynchronization();

        service.triggerScan(42L, List.of(entry), "publisher-1");

        verify(auditRepository).save(any(SecurityAudit.class));
        verify(skillVersionRepository).save(version);
        verify(scanTaskProducer, never()).publishScanTask(any(ScanTask.class));
        assertThat(version.getStatus()).isEqualTo(SkillVersionStatus.SCANNING);

        commitRegisteredSynchronizations();

        ArgumentCaptor<ScanTask> taskCaptor = ArgumentCaptor.forClass(ScanTask.class);
        verify(scanTaskProducer).publishScanTask(taskCaptor.capture());
        assertThat(taskCaptor.getValue().versionId()).isEqualTo(42L);
    }

    @Test
    void triggerScan_doesNotPublishTaskWhenTransactionNeverCommits() throws Exception {
        SkillVersion version = new SkillVersion(8L, "1.0.0", "publisher-1");
        setId(version, 42L);
        PackageEntry entry = new PackageEntry(
                "README.md",
                "# demo".getBytes(),
                6L,
                "text/markdown"
        );

        given(skillVersionRepository.findById(42L)).willReturn(Optional.of(version));
        TransactionSynchronizationManager.initSynchronization();

        service.triggerScan(42L, List.of(entry), "publisher-1");

        verify(auditRepository).save(any(SecurityAudit.class));
        verify(scanTaskProducer, never()).publishScanTask(any(ScanTask.class));
    }

    @Test
    void triggerScan_rejectsDirectoryTraversalEntries() throws Exception {
        SkillVersion version = new SkillVersion(8L, "1.0.0", "publisher-1");
        setId(version, 42L);
        PackageEntry entry = new PackageEntry(
                "../escape.txt",
                "boom".getBytes(),
                4L,
                "text/plain"
        );

        given(skillVersionRepository.findById(42L)).willReturn(Optional.of(version));

        assertThatThrownBy(() -> service.triggerScan(42L, List.of(entry), "publisher-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsafe scan path");
    }

    @Test
    void triggerScan_rejectsZipSlipEntriesWhenUploadModeEnabled() throws Exception {
        service = new SecurityScanService(
                auditRepository,
                skillVersionRepository,
                scanTaskProducer,
                new ObjectMapper(),
                "upload",
                true
        );
        SkillVersion version = new SkillVersion(8L, "1.0.0", "publisher-1");
        setId(version, 42L);
        PackageEntry entry = new PackageEntry(
                "../../escape.txt",
                "boom".getBytes(),
                4L,
                "text/plain"
        );

        given(skillVersionRepository.findById(42L)).willReturn(Optional.of(version));

        assertThatThrownBy(() -> service.triggerScan(42L, List.of(entry), "publisher-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsafe scan path");
    }

    @Test
    void processScanResult_updatesAuditAndMovesVersionToPendingReview() {
        SecurityAudit audit = new SecurityAudit(42L, ScannerType.SKILL_SCANNER, "task-current");
        SkillVersion version = new SkillVersion(8L, "1.0.0", "publisher-1");
        version.setStatus(SkillVersionStatus.SCANNING);

        given(auditRepository.findByTaskId("task-current")).willReturn(Optional.of(audit));
        given(auditRepository.findLatestActiveByVersionIdAndScannerType(42L, ScannerType.SKILL_SCANNER))
                .willReturn(Optional.of(audit));
        given(skillVersionRepository.findById(42L)).willReturn(Optional.of(version));

        SecurityScanResponse response = new SecurityScanResponse(
                "scan-123",
                SecurityVerdict.DANGEROUS,
                1,
                "HIGH",
                List.of(new SecurityFinding(
                        "STATIC-001",
                        "HIGH",
                        "code-execution",
                        "Dynamic execution detected",
                        "eval() should not be used here",
                        "src/main.py",
                        12,
                        "eval(user_input)"
                )),
                1.25
        );

        service.processScanResult("task-current", 42L, ScannerType.SKILL_SCANNER, response);

        assertThat(audit.getScanId()).isEqualTo("scan-123");
        assertThat(audit.getVerdict()).isEqualTo(SecurityVerdict.DANGEROUS);
        assertThat(audit.getIsSafe()).isFalse();
        assertThat(audit.getMaxSeverity()).isEqualTo("HIGH");
        assertThat(audit.getFindingsCount()).isEqualTo(1);
        assertThat(audit.getFindings()).contains("STATIC-001");
        assertThat(audit.getScanDurationSeconds()).isEqualTo(1.25);
        assertThat(audit.getScannedAt()).isNotNull();
        assertThat(version.getStatus()).isEqualTo(SkillVersionStatus.PENDING_REVIEW);
        verify(auditRepository).save(audit);
        verify(skillVersionRepository).save(version);
    }

    @Test
    void triggerScan_shouldNotChangeStatusWhenVersionAlreadyPublished() throws Exception {
        SkillVersion version = new SkillVersion(8L, "1.0.0", "publisher-1");
        setId(version, 42L);
        version.setStatus(SkillVersionStatus.PUBLISHED);
        PackageEntry entry = new PackageEntry(
                "README.md",
                "# demo".getBytes(),
                6L,
                "text/markdown"
        );

        given(skillVersionRepository.findById(42L)).willReturn(Optional.of(version));

        service.triggerScan(42L, List.of(entry), "publisher-1");

        verify(auditRepository).save(org.mockito.ArgumentMatchers.any(SecurityAudit.class));
        verify(scanTaskProducer).publishScanTask(org.mockito.ArgumentMatchers.any(ScanTask.class));
        assertThat(version.getStatus()).isEqualTo(SkillVersionStatus.PUBLISHED);
    }

    @Test
    void processScanFailure_marksExactCurrentAttemptAndVersionFailed() throws Exception {
        SecurityAudit audit = new SecurityAudit(42L, ScannerType.SKILL_SCANNER, "task-current");
        SkillVersion version = new SkillVersion(8L, "1.0.0", "publisher-1");
        setId(version, 42L);
        version.setStatus(SkillVersionStatus.SCANNING);
        given(auditRepository.findByTaskId("task-current")).willReturn(Optional.of(audit));
        given(auditRepository.findLatestActiveByVersionIdAndScannerType(42L, ScannerType.SKILL_SCANNER))
                .willReturn(Optional.of(audit));
        given(skillVersionRepository.findById(42L)).willReturn(Optional.of(version));

        service.processScanFailure("task-current", 42L, ScannerType.SKILL_SCANNER, "scanner unavailable");

        assertThat(audit.getScannedAt()).isNotNull();
        assertThat(audit.getFailureReason()).isEqualTo("scanner unavailable");
        assertThat(version.getStatus()).isEqualTo(SkillVersionStatus.SCAN_FAILED);
        verify(auditRepository).save(audit);
        verify(skillVersionRepository).save(version);
    }

    @Test
    void processScanFailure_forStaleAttemptDoesNotFailCurrentVersion() throws Exception {
        SecurityAudit stale = new SecurityAudit(42L, ScannerType.SKILL_SCANNER, "task-stale");
        SecurityAudit current = new SecurityAudit(42L, ScannerType.SKILL_SCANNER, "task-current");
        SkillVersion version = new SkillVersion(8L, "1.0.0", "publisher-1");
        setId(version, 42L);
        version.setStatus(SkillVersionStatus.SCANNING);
        given(auditRepository.findByTaskId("task-stale")).willReturn(Optional.of(stale));
        given(auditRepository.findLatestActiveByVersionIdAndScannerType(42L, ScannerType.SKILL_SCANNER))
                .willReturn(Optional.of(current));

        service.processScanFailure("task-stale", 42L, ScannerType.SKILL_SCANNER, "stale failure");

        assertThat(stale.getScannedAt()).isNotNull();
        assertThat(stale.getFailureReason()).isEqualTo("stale failure");
        assertThat(version.getStatus()).isEqualTo(SkillVersionStatus.SCANNING);
        verify(skillVersionRepository, never()).save(any());
    }

    @Test
    void processScanResult_shouldNotChangeStatusWhenVersionAlreadyPublished() {
        SecurityAudit audit = new SecurityAudit(42L, ScannerType.SKILL_SCANNER, "task-published");
        SkillVersion version = new SkillVersion(8L, "1.0.0", "publisher-1");
        version.setStatus(SkillVersionStatus.PUBLISHED);

        given(auditRepository.findByTaskId("task-published")).willReturn(Optional.of(audit));
        given(auditRepository.findLatestActiveByVersionIdAndScannerType(42L, ScannerType.SKILL_SCANNER))
                .willReturn(Optional.of(audit));
        given(skillVersionRepository.findById(42L)).willReturn(Optional.of(version));

        SecurityScanResponse response = new SecurityScanResponse(
                "scan-456",
                SecurityVerdict.SAFE,
                0,
                null,
                List.of(),
                0.5
        );

        service.processScanResult("task-published", 42L, ScannerType.SKILL_SCANNER, response);

        assertThat(audit.getVerdict()).isEqualTo(SecurityVerdict.SAFE);
        assertThat(audit.getIsSafe()).isTrue();
        assertThat(version.getStatus()).isEqualTo(SkillVersionStatus.PUBLISHED);
        verify(skillVersionRepository).save(version);
    }

    @Test
    void processScanResult_persistsScannerMaskedFindingWithoutOriginalCanary() {
        SecurityAudit audit = new SecurityAudit(42L, ScannerType.SKILL_SCANNER, "task-masked");
        SkillVersion version = new SkillVersion(8L, "1.0.0", "publisher-1");
        version.setStatus(SkillVersionStatus.SCANNING);

        given(auditRepository.findByTaskId("task-masked")).willReturn(Optional.of(audit));
        given(auditRepository.findLatestActiveByVersionIdAndScannerType(42L, ScannerType.SKILL_SCANNER))
                .willReturn(Optional.of(audit));
        given(skillVersionRepository.findById(42L)).willReturn(Optional.of(version));

        String canary = "ghp_012345678901234567890123456789012345";
        SecurityFinding maskedFinding = new SecurityFinding(
                "TOKEN-001", "HIGH", "secrets", "Token detected", "<redacted>",
                "SKILL.md", 4, "token=<redacted>", "Rotate token", "static", Map.of());
        service.processScanResult(
                "task-masked", 42L, ScannerType.SKILL_SCANNER,
                new SecurityScanResponse("scan-masked", SecurityVerdict.DANGEROUS, 1, "HIGH",
                        List.of(maskedFinding), 0.2));

        assertThat(audit.getFindings()).contains("<redacted>").doesNotContain(canary);
    }

    @Test
    void processScanResult_forStaleAttemptDoesNotCompleteCurrentAttempt() throws Exception {
        SecurityAudit stale = new SecurityAudit(42L, ScannerType.SKILL_SCANNER, "task-stale");
        SecurityAudit current = new SecurityAudit(42L, ScannerType.SKILL_SCANNER, "task-current");
        SkillVersion version = new SkillVersion(8L, "1.0.0", "publisher-1");
        setId(version, 42L);
        version.setStatus(SkillVersionStatus.SCANNING);
        given(auditRepository.findByTaskId("task-stale")).willReturn(Optional.of(stale));
        given(auditRepository.findLatestActiveByVersionIdAndScannerType(42L, ScannerType.SKILL_SCANNER))
                .willReturn(Optional.of(current));
        given(skillVersionRepository.findById(42L)).willReturn(Optional.of(version));

        service.processScanResult(
                "task-stale",
                42L,
                ScannerType.SKILL_SCANNER,
                new SecurityScanResponse("scan-stale", SecurityVerdict.SAFE, 0, null, List.of(), 0.1)
        );

        assertThat(stale.getScanId()).isEqualTo("scan-stale");
        assertThat(version.getStatus()).isEqualTo(SkillVersionStatus.SCANNING);
        verify(skillVersionRepository, never()).save(version);
    }

    private void setId(Object target, Long id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }

    private void commitRegisteredSynchronizations() {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
    }
}
