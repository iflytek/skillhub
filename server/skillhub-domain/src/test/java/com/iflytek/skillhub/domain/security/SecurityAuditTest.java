package com.iflytek.skillhub.domain.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SecurityAuditTest {

    @Test
    void constructorAndLifecycleInitializeExpectedDefaults() {
        SecurityAudit empty = new SecurityAudit();

        assertThat(empty.getId()).isNull();
        assertThat(empty.getSkillVersionId()).isNull();
        assertThat(empty.getScanId()).isNull();
        assertThat(empty.getScannerType()).isNull();
        assertThat(empty.getVerdict()).isNull();
        assertThat(empty.getIsSafe()).isNull();
        assertThat(empty.getMaxSeverity()).isNull();
        assertThat(empty.getFindingsCount()).isEqualTo(0);
        assertThat(empty.getFindings()).isNull();
        assertThat(empty.getScanDurationSeconds()).isNull();
        assertThat(empty.getScannedAt()).isNull();
        assertThat(empty.getCreatedAt()).isNull();
        assertThat(empty.getDeletedAt()).isNull();

        SecurityAudit audit = new SecurityAudit(42L, ScannerType.SKILL_SCANNER);

        assertThat(audit.getSkillVersionId()).isEqualTo(42L);
        assertThat(audit.getScannerType()).isEqualTo(ScannerType.SKILL_SCANNER);
        assertThat(audit.getVerdict()).isEqualTo(SecurityVerdict.SUSPICIOUS);
        assertThat(audit.getIsSafe()).isFalse();
        assertThat(audit.getFindingsCount()).isEqualTo(0);
        assertThat(audit.getFindings()).isEqualTo("[]");

        audit.onCreate();

        assertThat(audit.getCreatedAt()).isNotNull();
    }

    @Test
    void settersAndSoftDeleteHelpersReflectRuntimeState() {
        SecurityAudit audit = new SecurityAudit(42L, ScannerType.SKILL_SCANNER);
        Instant scannedAt = Instant.parse("2026-05-04T00:10:00Z");

        audit.setScanId("scan-1");
        audit.setVerdict(SecurityVerdict.BLOCKED);
        audit.setIsSafe(true);
        audit.setMaxSeverity("CRITICAL");
        audit.setFindingsCount(3);
        audit.setFindings("[{\"severity\":\"CRITICAL\"}]");
        audit.setScanDurationSeconds(1.5d);
        audit.setScannedAt(scannedAt);

        assertThat(audit.getScanId()).isEqualTo("scan-1");
        assertThat(audit.getVerdict()).isEqualTo(SecurityVerdict.BLOCKED);
        assertThat(audit.getIsSafe()).isTrue();
        assertThat(audit.getMaxSeverity()).isEqualTo("CRITICAL");
        assertThat(audit.getFindingsCount()).isEqualTo(3);
        assertThat(audit.getFindings()).isEqualTo("[{\"severity\":\"CRITICAL\"}]");
        assertThat(audit.getScanDurationSeconds()).isEqualTo(1.5d);
        assertThat(audit.getScannedAt()).isEqualTo(scannedAt);
        assertThat(audit.isDeleted()).isFalse();

        audit.markAsDeleted();

        assertThat(audit.isDeleted()).isTrue();
        assertThat(audit.getDeletedAt()).isNotNull();

        audit.restore();

        assertThat(audit.isDeleted()).isFalse();
        assertThat(audit.getDeletedAt()).isNull();
    }
}
