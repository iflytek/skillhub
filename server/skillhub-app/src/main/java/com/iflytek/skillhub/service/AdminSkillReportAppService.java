package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.report.SkillReport;
import com.iflytek.skillhub.domain.report.SkillReportRepository;
import com.iflytek.skillhub.domain.report.SkillReportStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.dto.AdminSkillReportSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.repository.AdminSkillReportQueryRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Application service that enriches raw skill report records with skill and
 * namespace context required by admin UIs.
 */
@Service
public class AdminSkillReportAppService {

    private final SkillReportRepository skillReportRepository;
    private final AdminSkillReportQueryRepository adminSkillReportQueryRepository;

    public AdminSkillReportAppService(SkillReportRepository skillReportRepository,
                                      AdminSkillReportQueryRepository adminSkillReportQueryRepository) {
        this.skillReportRepository = skillReportRepository;
        this.adminSkillReportQueryRepository = adminSkillReportQueryRepository;
    }

    public PageResponse<AdminSkillReportSummaryResponse> listReports(String status, int page, int size) {
        SkillReportStatus resolvedStatus = parseStatus(status);
        var reportPage = skillReportRepository.findByStatus(resolvedStatus, PageRequest.of(page, size));
        List<AdminSkillReportSummaryResponse> items = adminSkillReportQueryRepository.getSkillReportSummaries(reportPage.getContent());

        return new PageResponse<>(items, reportPage.getTotalElements(), reportPage.getNumber(), reportPage.getSize());
    }

    private SkillReportStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return SkillReportStatus.PENDING;
        }
        try {
            return SkillReportStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new DomainBadRequestException("error.skill.report.status.invalid", status);
        }
    }
}
