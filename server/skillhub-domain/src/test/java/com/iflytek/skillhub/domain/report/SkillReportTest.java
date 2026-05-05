package com.iflytek.skillhub.domain.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SkillReportTest {

    @Test
    void constructor_shouldSetFields() {
        SkillReport report = new SkillReport(1L, 2L, "user-1", "spam", "details here");

        assertThat(report.getSkillId()).isEqualTo(1L);
        assertThat(report.getNamespaceId()).isEqualTo(2L);
        assertThat(report.getReporterId()).isEqualTo("user-1");
        assertThat(report.getReason()).isEqualTo("spam");
        assertThat(report.getDetails()).isEqualTo("details here");
        assertThat(report.getStatus()).isEqualTo(SkillReportStatus.PENDING);
    }

    @Test
    void prePersist_shouldSetCreatedAt() {
        SkillReport report = new SkillReport(1L, 2L, "user-1", "spam", "details");
        assertThat(report.getCreatedAt()).isNull();

        report.onCreate();

        assertThat(report.getCreatedAt()).isNotNull();
        assertThat(report.getCreatedAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void gettersAndSetters_shouldWork() {
        SkillReport report = new SkillReport(1L, 2L, "user-1", "spam", "details");

        report.setStatus(SkillReportStatus.RESOLVED);
        assertThat(report.getStatus()).isEqualTo(SkillReportStatus.RESOLVED);

        report.setHandledBy("admin-1");
        assertThat(report.getHandledBy()).isEqualTo("admin-1");

        report.setHandleComment("handled");
        assertThat(report.getHandleComment()).isEqualTo("handled");

        Instant now = Instant.now();
        report.setHandledAt(now);
        assertThat(report.getHandledAt()).isEqualTo(now);
    }

    @Test
    void protectedConstructor_shouldCreateEmptyInstance() {
        // Exercise the protected no-arg constructor via reflection
        SkillReport report = new SkillReport() {};
        assertThat(report.getId()).isNull();
    }
}
