package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.report.SkillReport;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.dto.AdminSkillReportSummaryResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class JpaAdminSkillReportQueryRepository implements AdminSkillReportQueryRepository {

    private final SkillRepository skillRepository;
    private final NamespaceRepository namespaceRepository;

    public JpaAdminSkillReportQueryRepository(SkillRepository skillRepository,
                                              NamespaceRepository namespaceRepository) {
        this.skillRepository = skillRepository;
        this.namespaceRepository = namespaceRepository;
    }

    @Override
    public List<AdminSkillReportSummaryResponse> getSkillReportSummaries(List<SkillReport> reports) {
        if (reports.isEmpty()) {
            return List.of();
        }
        List<Long> skillIds = reports.stream().map(SkillReport::getSkillId).distinct().toList();
        Map<Long, Skill> skillsById = skillIds.isEmpty()
                ? Map.of()
                : skillRepository.findByIdIn(skillIds).stream()
                        .collect(Collectors.toMap(Skill::getId, Function.identity()));

        List<Long> namespaceIds = skillsById.values().stream().map(Skill::getNamespaceId).distinct().toList();
        Map<Long, String> namespaceSlugs = namespaceIds.isEmpty()
                ? Map.of()
                : namespaceRepository.findByIdIn(namespaceIds).stream()
                        .collect(Collectors.toMap(Namespace::getId, Namespace::getSlug));

        return reports.stream()
                .map(report -> toResponse(report, skillsById.get(report.getSkillId()), namespaceSlugs))
                .toList();
    }

    private AdminSkillReportSummaryResponse toResponse(SkillReport report,
                                                       Skill skill,
                                                       Map<Long, String> namespaceSlugs) {
        return new AdminSkillReportSummaryResponse(
                report.getId(),
                report.getSkillId(),
                skill != null ? namespaceSlugs.get(skill.getNamespaceId()) : null,
                skill != null ? skill.getSlug() : null,
                skill != null ? skill.getDisplayName() : null,
                report.getReporterId(),
                report.getReason(),
                report.getDetails(),
                report.getStatus().name(),
                report.getHandledBy(),
                report.getHandleComment(),
                report.getCreatedAt(),
                report.getHandledAt()
        );
    }
}
