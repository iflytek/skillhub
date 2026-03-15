package com.iflytek.skillhub.domain.report;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillReportService {

    private final SkillRepository skillRepository;
    private final SkillReportRepository skillReportRepository;
    private final AuditLogService auditLogService;

    public SkillReportService(SkillRepository skillRepository,
                              SkillReportRepository skillReportRepository,
                              AuditLogService auditLogService) {
        this.skillRepository = skillRepository;
        this.skillReportRepository = skillReportRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public SkillReport submitReport(Long skillId,
                                    String reporterId,
                                    String reason,
                                    String details,
                                    String clientIp,
                                    String userAgent) {
        if (reason == null || reason.isBlank()) {
            throw new DomainBadRequestException("error.skill.report.reason.required");
        }

        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new DomainNotFoundException("error.skill.notFound", skillId));
        if (skill.getStatus() != SkillStatus.ACTIVE || skill.isHidden()) {
            throw new DomainBadRequestException("error.skill.report.unavailable", skill.getSlug());
        }
        if (skill.getOwnerId().equals(reporterId)) {
            throw new DomainBadRequestException("error.skill.report.self");
        }
        if (skillReportRepository.existsBySkillIdAndReporterIdAndStatus(skillId, reporterId, SkillReportStatus.PENDING)) {
            throw new DomainBadRequestException("error.skill.report.duplicate");
        }

        SkillReport saved = skillReportRepository.save(new SkillReport(
                skillId,
                skill.getNamespaceId(),
                reporterId,
                reason.trim(),
                normalize(details)
        ));
        auditLogService.record(reporterId, "REPORT_SKILL", "SKILL", skillId, null, clientIp, userAgent,
                "{\"reportId\":" + saved.getId() + "}");
        return saved;
    }

    @Transactional
    public SkillReport resolveReport(Long reportId,
                                     String actorUserId,
                                     String comment,
                                     String clientIp,
                                     String userAgent) {
        SkillReport report = requirePendingReport(reportId);
        report.setStatus(SkillReportStatus.RESOLVED);
        report.setHandledBy(actorUserId);
        report.setHandleComment(normalize(comment));
        report.setHandledAt(LocalDateTime.now());
        SkillReport saved = skillReportRepository.save(report);
        auditLogService.record(actorUserId, "RESOLVE_SKILL_REPORT", "SKILL_REPORT", reportId, null, clientIp, userAgent, null);
        return saved;
    }

    @Transactional
    public SkillReport dismissReport(Long reportId,
                                     String actorUserId,
                                     String comment,
                                     String clientIp,
                                     String userAgent) {
        SkillReport report = requirePendingReport(reportId);
        report.setStatus(SkillReportStatus.DISMISSED);
        report.setHandledBy(actorUserId);
        report.setHandleComment(normalize(comment));
        report.setHandledAt(LocalDateTime.now());
        SkillReport saved = skillReportRepository.save(report);
        auditLogService.record(actorUserId, "DISMISS_SKILL_REPORT", "SKILL_REPORT", reportId, null, clientIp, userAgent, null);
        return saved;
    }

    private SkillReport requirePendingReport(Long reportId) {
        SkillReport report = skillReportRepository.findById(reportId)
                .orElseThrow(() -> new DomainNotFoundException("error.skill.report.notFound", reportId));
        if (report.getStatus() != SkillReportStatus.PENDING) {
            throw new DomainBadRequestException("error.skill.report.alreadyHandled");
        }
        return report;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
