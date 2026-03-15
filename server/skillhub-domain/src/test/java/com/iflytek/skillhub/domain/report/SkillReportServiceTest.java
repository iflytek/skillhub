package com.iflytek.skillhub.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SkillReportServiceTest {

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private SkillReportRepository skillReportRepository;

    @Mock
    private AuditLogService auditLogService;

    private SkillReportService service;

    @BeforeEach
    void setUp() {
        service = new SkillReportService(skillRepository, skillReportRepository, auditLogService);
    }

    @Test
    void submitReport_createsPendingReport() {
        Skill skill = new Skill(1L, "demo", "owner", SkillVisibility.PUBLIC);
        setField(skill, "id", 10L);
        when(skillRepository.findById(10L)).thenReturn(Optional.of(skill));
        when(skillReportRepository.existsBySkillIdAndReporterIdAndStatus(10L, "user-1", SkillReportStatus.PENDING)).thenReturn(false);
        when(skillReportRepository.save(any(SkillReport.class))).thenAnswer(invocation -> {
            SkillReport report = invocation.getArgument(0);
            setField(report, "id", 99L);
            return report;
        });

        SkillReport report = service.submitReport(10L, "user-1", "Inappropriate content", "details", "127.0.0.1", "JUnit");

        assertThat(report.getStatus()).isEqualTo(SkillReportStatus.PENDING);
        assertThat(report.getReason()).isEqualTo("Inappropriate content");
        verify(auditLogService).record("user-1", "REPORT_SKILL", "SKILL", 10L, null, "127.0.0.1", "JUnit", "{\"reportId\":99}");
    }

    @Test
    void submitReport_rejectsDuplicatePendingReport() {
        Skill skill = new Skill(1L, "demo", "owner", SkillVisibility.PUBLIC);
        setField(skill, "id", 10L);
        when(skillRepository.findById(10L)).thenReturn(Optional.of(skill));
        when(skillReportRepository.existsBySkillIdAndReporterIdAndStatus(10L, "user-1", SkillReportStatus.PENDING)).thenReturn(true);

        assertThrows(DomainBadRequestException.class,
                () -> service.submitReport(10L, "user-1", "Inappropriate content", null, "127.0.0.1", "JUnit"));
    }

    @Test
    void submitReport_rejectsSelfReport() {
        Skill skill = new Skill(1L, "demo", "owner", SkillVisibility.PUBLIC);
        setField(skill, "id", 10L);
        when(skillRepository.findById(10L)).thenReturn(Optional.of(skill));

        assertThrows(DomainBadRequestException.class,
                () -> service.submitReport(10L, "owner", "Inappropriate content", null, "127.0.0.1", "JUnit"));
    }

    @Test
    void resolveReport_marksReportResolved() {
        SkillReport report = new SkillReport(10L, 1L, "user-1", "spam", null);
        setField(report, "id", 99L);
        when(skillReportRepository.findById(99L)).thenReturn(Optional.of(report));
        when(skillReportRepository.save(report)).thenReturn(report);

        SkillReport saved = service.resolveReport(99L, "admin", "handled", "127.0.0.1", "JUnit");

        assertThat(saved.getStatus()).isEqualTo(SkillReportStatus.RESOLVED);
        assertThat(saved.getHandledBy()).isEqualTo("admin");
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
