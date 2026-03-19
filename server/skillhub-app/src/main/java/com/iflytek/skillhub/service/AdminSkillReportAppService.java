package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.report.SkillReport;
import com.iflytek.skillhub.domain.report.SkillReportRepository;
import com.iflytek.skillhub.domain.report.SkillReportStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.dto.AdminSkillReportSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Application service that enriches raw skill report records with skill and
 * namespace context required by admin UIs.
 */
@Service
public class AdminSkillReportAppService {

    private final SkillReportRepository skillReportRepository;
    private final SkillRepository skillRepository;
    private final NamespaceRepository namespaceRepository;

    public AdminSkillReportAppService(SkillReportRepository skillReportRepository,
                                      SkillRepository skillRepository,
                                      NamespaceRepository namespaceRepository) {
        this.skillReportRepository = skillReportRepository;
        this.skillRepository = skillRepository;
        this.namespaceRepository = namespaceRepository;
    }

    public PageResponse<AdminSkillReportSummaryResponse> listReports(String status, int page, int size) {
        SkillReportStatus resolvedStatus = parseStatus(status);
        var reportPage = skillReportRepository.findByStatus(resolvedStatus, PageRequest.of(page, size));

        List<Long> skillIds = reportPage.getContent().stream().map(SkillReport::getSkillId).distinct().toList();
        Map<Long, Skill> skillsById = skillIds.isEmpty()
                ? Map.of()
                : skillRepository.findByIdIn(skillIds).stream().collect(Collectors.toMap(Skill::getId, Function.identity()));

        List<Long> namespaceIds = skillsById.values().stream().map(Skill::getNamespaceId).distinct().toList();
        Map<Long, String> namespaceSlugs = namespaceIds.isEmpty()
                ? Map.of()
                : namespaceRepository.findByIdIn(namespaceIds).stream().collect(Collectors.toMap(Namespace::getId, Namespace::getSlug));

        List<AdminSkillReportSummaryResponse> items = reportPage.getContent().stream()
                .map(report -> toResponse(report, skillsById.get(report.getSkillId()), namespaceSlugs))
                .toList();

        return new PageResponse<>(items, reportPage.getTotalElements(), reportPage.getNumber(), reportPage.getSize());
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
