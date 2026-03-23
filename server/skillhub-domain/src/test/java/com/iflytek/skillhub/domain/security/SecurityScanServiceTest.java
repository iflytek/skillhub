package com.iflytek.skillhub.domain.security;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SecurityScanServiceTest {

    @Mock
    private SecurityAuditRepository auditRepository;

    @Mock
    private SkillVersionRepository skillVersionRepository;

    @Mock
    private ScanTaskProducer scanTaskProducer;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SecurityScanService service;

    @BeforeEach
    void setUp() {
        service = new SecurityScanService(
                auditRepository,
                skillVersionRepository,
                scanTaskProducer,
                eventPublisher,
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
                eventPublisher,
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
        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.any(ScanCompletedEvent.class));
    }

    private void setId(Object target, Long id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
