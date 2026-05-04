package com.iflytek.skillhub.domain.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
    void triggerScan_skipsWhenDisabled() {
        service = new SecurityScanService(
                auditRepository,
                skillVersionRepository,
                scanTaskProducer,
                new ObjectMapper(),
                "local",
                false
        );

        service.triggerScan(42L, List.of(), "publisher-1");

        verify(skillVersionRepository, never()).findById(any());
        verify(auditRepository, never()).save(any());
        verify(scanTaskProducer, never()).publishScanTask(any());
    }

    @Test
    void isEnabled_returnsConfiguredValue() {
        assertThat(service.isEnabled()).isTrue();

        SecurityScanService disabled = new SecurityScanService(
                auditRepository, skillVersionRepository, scanTaskProducer, new ObjectMapper(), "local", false);
        assertThat(disabled.isEnabled()).isFalse();
    }

    @Test
    void processScanResult_updatesAuditAndMovesVersionToPendingReview() {
        SecurityAudit audit = new SecurityAudit(42L, ScannerType.SKILL_SCANNER);
        SkillVersion version = new SkillVersion(8L, "1.0.0", "publisher-1");

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

        service.processScanResult(42L, ScannerType.SKILL_SCANNER, response);

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
    void processScanResult_marksSafeWhenVerdictIsSafe() {
        SecurityAudit audit = new SecurityAudit(42L, ScannerType.SKILL_SCANNER);
        SkillVersion version = new SkillVersion(8L, "1.0.0", "publisher-1");

        given(auditRepository.findLatestActiveByVersionIdAndScannerType(42L, ScannerType.SKILL_SCANNER))
                .willReturn(Optional.of(audit));
        given(skillVersionRepository.findById(42L)).willReturn(Optional.of(version));

        SecurityScanResponse response = new SecurityScanResponse(
                "scan-123", SecurityVerdict.SAFE, 0, null, List.of(), 0.5
        );

        service.processScanResult(42L, ScannerType.SKILL_SCANNER, response);

        assertThat(audit.getIsSafe()).isTrue();
    }

    @Test
    void processScanResult_movesToUploadedWhenPrivate() {
        SecurityAudit audit = new SecurityAudit(42L, ScannerType.SKILL_SCANNER);
        SkillVersion version = new SkillVersion(8L, "1.0.0", "publisher-1");
        version.setRequestedVisibility(SkillVisibility.PRIVATE);

        given(auditRepository.findLatestActiveByVersionIdAndScannerType(42L, ScannerType.SKILL_SCANNER))
                .willReturn(Optional.of(audit));
        given(skillVersionRepository.findById(42L)).willReturn(Optional.of(version));

        SecurityScanResponse response = new SecurityScanResponse(
                "scan-123", SecurityVerdict.SAFE, 0, null, List.of(), 0.5
        );

        service.processScanResult(42L, ScannerType.SKILL_SCANNER, response);

        assertThat(version.getStatus()).isEqualTo(SkillVersionStatus.UPLOADED);
    }

    @Test
    void safeZipEntryName_rejectsBlankPath() throws Exception {
        service = new SecurityScanService(
                auditRepository, skillVersionRepository, scanTaskProducer, new ObjectMapper(), "upload", true);
        SkillVersion version = new SkillVersion(8L, "1.0.0", "publisher-1");
        setId(version, 42L);
        PackageEntry entry = new PackageEntry("", "boom".getBytes(), 4L, "text/plain");

        given(skillVersionRepository.findById(42L)).willReturn(Optional.of(version));

        assertThatThrownBy(() -> service.triggerScan(42L, List.of(entry), "publisher-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsafe scan path");
    }

    @Test
    void safeZipEntryName_rejectsRelativePath() throws Exception {
        service = new SecurityScanService(
                auditRepository, skillVersionRepository, scanTaskProducer, new ObjectMapper(), "upload", true);
        SkillVersion version = new SkillVersion(8L, "1.0.0", "publisher-1");
        setId(version, 42L);
        PackageEntry entry = new PackageEntry("../etc/passwd", "boom".getBytes(), 4L, "text/plain");

        given(skillVersionRepository.findById(42L)).willReturn(Optional.of(version));

        assertThatThrownBy(() -> service.triggerScan(42L, List.of(entry), "publisher-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsafe scan path");
    }

    @Test
    void safeZipEntryName_rejectsAbsolutePath() throws Exception {
        service = new SecurityScanService(
                auditRepository, skillVersionRepository, scanTaskProducer, new ObjectMapper(), "upload", true);
        SkillVersion version = new SkillVersion(8L, "1.0.0", "publisher-1");
        setId(version, 42L);
        PackageEntry entry = new PackageEntry("/etc/passwd", "boom".getBytes(), 4L, "text/plain");

        given(skillVersionRepository.findById(42L)).willReturn(Optional.of(version));

        assertThatThrownBy(() -> service.triggerScan(42L, List.of(entry), "publisher-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsafe scan path");
    }

    @Test
    void serializeFindings_fallsBackToEmptyArrayOnError() {
        ObjectMapper badMapper = mock(ObjectMapper.class);
        service = new SecurityScanService(
                auditRepository, skillVersionRepository, scanTaskProducer, badMapper, "local", true);
        SecurityAudit audit = new SecurityAudit(42L, ScannerType.SKILL_SCANNER);
        SkillVersion version = new SkillVersion(8L, "1.0.0", "publisher-1");

        given(auditRepository.findLatestActiveByVersionIdAndScannerType(42L, ScannerType.SKILL_SCANNER))
                .willReturn(Optional.of(audit));
        given(skillVersionRepository.findById(42L)).willReturn(Optional.of(version));

        SecurityScanResponse response = new SecurityScanResponse(
                "scan-123", SecurityVerdict.SAFE, 0, null,
                List.of(new SecurityFinding("F-1", "HIGH", "type", "msg", "fix", "file.py", 1, "code")),
                0.5
        );

        try {
            when(badMapper.writeValueAsString(response.findings())).thenThrow(new JsonProcessingException("fail") {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        service.processScanResult(42L, ScannerType.SKILL_SCANNER, response);

        assertThat(audit.getFindings()).isEqualTo("[]");
    }

    @Test
    void softDeleteByVersionId_skipsWhenNoAudits() {
        when(auditRepository.findAllActiveBySkillVersionId(42L)).thenReturn(List.of());

        service.softDeleteByVersionId(42L);

        verify(auditRepository, never()).saveAll(any());
    }

    @Test
    void softDeleteByVersionId_marksAuditsAsDeleted() {
        SecurityAudit audit = new SecurityAudit(42L, ScannerType.SKILL_SCANNER);
        when(auditRepository.findAllActiveBySkillVersionId(42L)).thenReturn(List.of(audit));

        service.softDeleteByVersionId(42L);

        assertThat(audit.isDeleted()).isTrue();
        verify(auditRepository).saveAll(List.of(audit));
    }

    @Test
    void hardDeleteByVersionId_deletesAll() {
        service.hardDeleteByVersionId(42L);
        verify(auditRepository).deleteBySkillVersionId(42L);
    }

    private void setId(Object target, Long id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
