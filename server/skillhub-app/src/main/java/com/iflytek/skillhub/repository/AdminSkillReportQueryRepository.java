package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.report.SkillReport;
import com.iflytek.skillhub.dto.AdminSkillReportSummaryResponse;
import java.util.List;

/**
 * Query-side repository for admin-facing skill report summary rows.
 */
public interface AdminSkillReportQueryRepository {
    List<AdminSkillReportSummaryResponse> getSkillReportSummaries(List<SkillReport> reports);
}
