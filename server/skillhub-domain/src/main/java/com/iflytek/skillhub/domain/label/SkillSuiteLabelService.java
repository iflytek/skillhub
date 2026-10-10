package com.iflytek.skillhub.domain.label;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.suite.SkillSuite;
import com.iflytek.skillhub.domain.suite.SkillSuiteRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Manages direct Suite labels without touching Suite versions or member Skill metadata. */
@Service
public class SkillSuiteLabelService {

    private final int maxLabelsPerSuite;
    private final SkillSuiteRepository suiteRepository;
    private final LabelDefinitionRepository labelDefinitionRepository;
    private final SkillSuiteLabelRepository suiteLabelRepository;
    private final LabelPermissionChecker labelPermissionChecker;

    public SkillSuiteLabelService(
            SkillSuiteRepository suiteRepository,
            LabelDefinitionRepository labelDefinitionRepository,
            SkillSuiteLabelRepository suiteLabelRepository,
            LabelPermissionChecker labelPermissionChecker,
            @Value("${skillhub.label.max-per-suite:10}") int maxLabelsPerSuite
    ) {
        this.suiteRepository = suiteRepository;
        this.labelDefinitionRepository = labelDefinitionRepository;
        this.suiteLabelRepository = suiteLabelRepository;
        this.labelPermissionChecker = labelPermissionChecker;
        if (maxLabelsPerSuite <= 0) {
            throw new IllegalArgumentException("skillhub.label.max-per-suite must be greater than 0");
        }
        this.maxLabelsPerSuite = maxLabelsPerSuite;
    }

    public List<SkillSuiteLabel> listSuiteLabels(Long suiteId) {
        return suiteLabelRepository.findBySuiteId(suiteId);
    }

    public List<SkillSuiteLabel> listSuiteLabelsBySuiteIds(List<Long> suiteIds) {
        if (suiteIds == null || suiteIds.isEmpty()) {
            return List.of();
        }
        return suiteLabelRepository.findBySuiteIdIn(suiteIds);
    }

    public List<SkillSuiteLabel> listByLabelId(Long labelId) {
        return suiteLabelRepository.findByLabelId(labelId);
    }

    @Transactional
    public SkillSuiteLabel attachLabel(
            Long suiteId,
            String labelSlug,
            String operatorId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        SkillSuite suite = findSuite(suiteId);
        LabelDefinition label = findLabel(labelSlug);
        requirePermission(suite, label, operatorId, namespaceRoles, platformRoles);

        return suiteLabelRepository.findBySuiteIdAndLabelId(suiteId, label.getId())
                .orElseGet(() -> {
                    List<SkillSuiteLabel> existing = suiteLabelRepository.findBySuiteId(suiteId);
                    if (existing.size() >= maxLabelsPerSuite) {
                        throw new DomainBadRequestException(
                                "label.suite.too_many", suiteId, maxLabelsPerSuite);
                    }
                    return suiteLabelRepository.save(
                            new SkillSuiteLabel(suiteId, label.getId(), operatorId));
                });
    }

    @Transactional
    public void detachLabel(
            Long suiteId,
            String labelSlug,
            String operatorId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        SkillSuite suite = findSuite(suiteId);
        LabelDefinition label = findLabel(labelSlug);
        requirePermission(suite, label, operatorId, namespaceRoles, platformRoles);
        SkillSuiteLabel suiteLabel = suiteLabelRepository.findBySuiteIdAndLabelId(suiteId, label.getId())
                .orElseThrow(() -> new DomainBadRequestException(
                        "label.suite.not_found", suiteId, labelSlug));
        suiteLabelRepository.delete(suiteLabel);
    }

    private SkillSuite findSuite(Long suiteId) {
        return suiteRepository.findById(suiteId)
                .orElseThrow(() -> new DomainBadRequestException("error.suite.notFound", suiteId));
    }

    private LabelDefinition findLabel(String labelSlug) {
        String normalized = LabelSlugValidator.normalize(labelSlug);
        return labelDefinitionRepository.findBySlugIgnoreCase(normalized)
                .orElseThrow(() -> new DomainBadRequestException("label.not_found", normalized));
    }

    private void requirePermission(
            SkillSuite suite,
            LabelDefinition label,
            String operatorId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        if (!labelPermissionChecker.canManageSuiteLabel(
                suite, label, operatorId, namespaceRoles, platformRoles)) {
            throw new DomainForbiddenException("label.suite.no_permission");
        }
    }
}
