package com.iflytek.skillhub.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.governance.GovernanceNotificationService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillGovernanceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SkillReportServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-18T08:00:00Z"), ZoneOffset.UTC);

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private SkillReportRepository skillReportRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private SkillGovernanceService skillGovernanceService;

    @Mock
    private GovernanceNotificationService governanceNotificationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SkillReportService service;

    @BeforeEach
    void setUp() {
        service = new SkillReportService(
                skillRepository,
                skillReportRepository,
                auditLogService,
                skillGovernanceService,
                governanceNotificationService,
                eventPublisher,
                CLOCK
        );
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
    void submitReport_rejectsNullReason() {
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.submitReport(10L, "user-1", null, null, "127.0.0.1", "JUnit"));
        assertThat(ex.messageCode()).isEqualTo("error.skill.report.reason.required");
    }

    @Test
    void submitReport_rejectsBlankReason() {
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.submitReport(10L, "user-1", "   ", null, "127.0.0.1", "JUnit"));
        assertThat(ex.messageCode()).isEqualTo("error.skill.report.reason.required");
    }

    @Test
    void submitReport_rejectsInactiveSkill() {
        Skill skill = new Skill(1L, "demo", "owner", SkillVisibility.PUBLIC);
        setField(skill, "id", 10L);
        skill.setStatus(SkillStatus.ARCHIVED);
        when(skillRepository.findById(10L)).thenReturn(Optional.of(skill));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.submitReport(10L, "user-1", "spam", null, "127.0.0.1", "JUnit"));
        assertThat(ex.messageCode()).isEqualTo("error.skill.report.unavailable");
    }

    @Test
    void submitReport_rejectsHiddenSkill() {
        Skill skill = new Skill(1L, "demo", "owner", SkillVisibility.PUBLIC);
        setField(skill, "id", 10L);
        skill.setHidden(true);
        when(skillRepository.findById(10L)).thenReturn(Optional.of(skill));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.submitReport(10L, "user-1", "spam", null, "127.0.0.1", "JUnit"));
        assertThat(ex.messageCode()).isEqualTo("error.skill.report.unavailable");
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
        assertThat(saved.getHandledAt()).isEqualTo(Instant.now(CLOCK));
    }

    @Test
    void resolveReport_withHideDisposition_hidesSkillAndNotifiesReporter() {
        SkillReport report = new SkillReport(10L, 1L, "user-1", "spam", null);
        setField(report, "id", 99L);
        when(skillReportRepository.findById(99L)).thenReturn(Optional.of(report));
        when(skillReportRepository.save(report)).thenReturn(report);

        SkillReport saved = service.resolveReport(
                99L,
                "admin",
                SkillReportDisposition.RESOLVE_AND_HIDE,
                "handled",
                "127.0.0.1",
                "JUnit"
        );

        assertThat(saved.getStatus()).isEqualTo(SkillReportStatus.RESOLVED);
        verify(skillGovernanceService).hideSkill(10L, "admin", "127.0.0.1", "JUnit", "handled");
        verify(governanceNotificationService).notifyUser(
                eq("user-1"),
                eq("REPORT"),
                eq("SKILL_REPORT"),
                eq(99L),
                eq("Report handled"),
                any()
        );
    }

    @Test
    void resolveReport_withArchiveDisposition_archivesSkill() {
        SkillReport report = new SkillReport(10L, 1L, "user-1", "spam", null);
        setField(report, "id", 99L);
        when(skillReportRepository.findById(99L)).thenReturn(Optional.of(report));
        when(skillReportRepository.save(report)).thenReturn(report);

        service.resolveReport(
                99L,
                "admin",
                SkillReportDisposition.RESOLVE_AND_ARCHIVE,
                "handled",
                "127.0.0.1",
                "JUnit"
        );

        verify(skillGovernanceService).archiveSkillAsAdmin(10L, "admin", "127.0.0.1", "JUnit", "handled");
    }

    @Test
    void dismissReport_marksReportDismissed() {
        SkillReport report = new SkillReport(10L, 1L, "user-1", "spam", null);
        setField(report, "id", 99L);
        when(skillReportRepository.findById(99L)).thenReturn(Optional.of(report));
        when(skillReportRepository.save(report)).thenReturn(report);

        SkillReport saved = service.dismissReport(99L, "admin", "not valid", "127.0.0.1", "JUnit");

        assertThat(saved.getStatus()).isEqualTo(SkillReportStatus.DISMISSED);
        assertThat(saved.getHandledBy()).isEqualTo("admin");
        assertThat(saved.getHandledAt()).isEqualTo(Instant.now(CLOCK));
        verify(auditLogService).record("admin", "DISMISS_SKILL_REPORT", "SKILL_REPORT", 99L, null, "127.0.0.1", "JUnit", null);
        verify(governanceNotificationService).notifyUser(
                eq("user-1"), eq("REPORT"), eq("SKILL_REPORT"), eq(99L), eq("Report dismissed"), any()
        );
    }

    @Test
    void requirePendingReport_throwsWhenNotFound() {
        when(skillReportRepository.findById(99L)).thenReturn(Optional.empty());

        DomainNotFoundException ex = assertThrows(DomainNotFoundException.class,
                () -> service.resolveReport(99L, "admin", "ok", "127.0.0.1", "JUnit"));
        assertThat(ex.messageCode()).isEqualTo("error.skill.report.notFound");
    }

    @Test
    void requirePendingReport_throwsWhenAlreadyHandled() {
        SkillReport report = new SkillReport(10L, 1L, "user-1", "spam", null);
        setField(report, "id", 99L);
        report.setStatus(SkillReportStatus.RESOLVED);
        when(skillReportRepository.findById(99L)).thenReturn(Optional.of(report));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.resolveReport(99L, "admin", "ok", "127.0.0.1", "JUnit"));
        assertThat(ex.messageCode()).isEqualTo("error.skill.report.alreadyHandled");
    }

    @Test
    void normalize_returnsNullForNull() {
        SkillReport report = new SkillReport(10L, 1L, "user-1", "spam", null);
        setField(report, "id", 99L);
        when(skillReportRepository.findById(99L)).thenReturn(Optional.of(report));
        when(skillReportRepository.save(report)).thenReturn(report);

        SkillReport saved = service.dismissReport(99L, "admin", null, "127.0.0.1", "JUnit");
        assertThat(saved.getHandleComment()).isNull();
    }

    @Test
    void normalize_returnsNullForEmptyAfterTrim() {
        SkillReport report = new SkillReport(10L, 1L, "user-1", "spam", null);
        setField(report, "id", 99L);
        when(skillReportRepository.findById(99L)).thenReturn(Optional.of(report));
        when(skillReportRepository.save(report)).thenReturn(report);

        SkillReport saved = service.dismissReport(99L, "admin", "   ", "127.0.0.1", "JUnit");
        assertThat(saved.getHandleComment()).isNull();
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
