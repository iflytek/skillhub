package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.label.LabelDefinition;
import com.iflytek.skillhub.domain.label.LabelDefinitionService;
import com.iflytek.skillhub.domain.label.LabelTranslation;
import com.iflytek.skillhub.domain.label.SkillSuiteLabel;
import com.iflytek.skillhub.domain.label.SkillSuiteLabelService;
import com.iflytek.skillhub.dto.SkillLabelDto;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Projects direct Suite labels for a page of Suites with a bounded query count. */
@Service
public class SkillSuiteLabelProjectionService {

    private final SkillSuiteLabelService suiteLabelService;
    private final LabelDefinitionService labelDefinitionService;
    private final LabelLocalizationService labelLocalizationService;

    public SkillSuiteLabelProjectionService(
            SkillSuiteLabelService suiteLabelService,
            LabelDefinitionService labelDefinitionService,
            LabelLocalizationService labelLocalizationService
    ) {
        this.suiteLabelService = suiteLabelService;
        this.labelDefinitionService = labelDefinitionService;
        this.labelLocalizationService = labelLocalizationService;
    }

    public Map<Long, List<SkillLabelDto>> labelsBySuiteIds(List<Long> suiteIds) {
        if (suiteIds == null || suiteIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinctSuiteIds = suiteIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctSuiteIds.isEmpty()) {
            return Map.of();
        }
        List<SkillSuiteLabel> assignments =
                suiteLabelService.listSuiteLabelsBySuiteIds(distinctSuiteIds);
        if (assignments.isEmpty()) {
            return Map.of();
        }
        List<Long> labelIds = assignments.stream()
                .map(SkillSuiteLabel::getLabelId)
                .distinct()
                .toList();
        Map<Long, LabelDefinition> definitionsById = labelDefinitionService.listByIds(labelIds).stream()
                .collect(Collectors.toMap(LabelDefinition::getId, Function.identity()));
        Map<Long, List<LabelTranslation>> translationsByLabelId =
                labelDefinitionService.listTranslationsByLabelIds(labelIds);

        return assignments.stream()
                .filter(assignment -> definitionsById.containsKey(assignment.getLabelId()))
                .collect(Collectors.groupingBy(
                        SkillSuiteLabel::getSuiteId,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                suiteAssignments -> suiteAssignments.stream()
                                        .map(assignment -> toDto(
                                                definitionsById.get(assignment.getLabelId()),
                                                translationsByLabelId))
                                        .sorted(Comparator.comparing(SkillLabelDto::type)
                                                .thenComparing(SkillLabelDto::slug))
                                        .toList())));
    }

    private SkillLabelDto toDto(
            LabelDefinition definition,
            Map<Long, List<LabelTranslation>> translationsByLabelId
    ) {
        return new SkillLabelDto(
                definition.getSlug(),
                definition.getType().name(),
                labelLocalizationService.resolveDisplayName(
                        definition.getSlug(),
                        translationsByLabelId.getOrDefault(definition.getId(), List.of())));
    }
}
