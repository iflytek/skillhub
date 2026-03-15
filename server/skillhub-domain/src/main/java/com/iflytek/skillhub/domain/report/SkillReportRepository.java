package com.iflytek.skillhub.domain.report;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SkillReportRepository {
    SkillReport save(SkillReport report);
    Optional<SkillReport> findById(Long id);
    boolean existsBySkillIdAndReporterIdAndStatus(Long skillId, String reporterId, SkillReportStatus status);
    Page<SkillReport> findByStatus(SkillReportStatus status, Pageable pageable);
    List<SkillReport> findBySkillIdIn(Collection<Long> skillIds);
}
