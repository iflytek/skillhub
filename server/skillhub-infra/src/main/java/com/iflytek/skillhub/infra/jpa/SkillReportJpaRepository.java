package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.report.SkillReport;
import com.iflytek.skillhub.domain.report.SkillReportRepository;
import com.iflytek.skillhub.domain.report.SkillReportStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA-backed repository for skill abuse reports and report queues ordered by creation time.
 */
public interface SkillReportJpaRepository extends JpaRepository<SkillReport, Long>, SkillReportRepository {
    boolean existsBySkillIdAndReporterIdAndStatus(Long skillId, String reporterId, SkillReportStatus status);
    Page<SkillReport> findByStatusOrderByCreatedAtDesc(SkillReportStatus status, Pageable pageable);
    List<SkillReport> findBySkillIdIn(Collection<Long> skillIds);

    @Override
    default Page<SkillReport> findByStatus(SkillReportStatus status, Pageable pageable) {
        return findByStatusOrderByCreatedAtDesc(status, pageable);
    }
}
