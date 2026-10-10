package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.label.LabelDefinition;
import com.iflytek.skillhub.domain.label.LabelDefinitionService;
import com.iflytek.skillhub.domain.label.LabelTranslation;
import com.iflytek.skillhub.domain.label.SkillLabel;
import com.iflytek.skillhub.domain.label.SkillLabelService;
import com.iflytek.skillhub.dto.SkillLabelDto;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Projects skill labels for a whole page of skills in a fixed number of queries.
 *
 * <p>Listing endpoints need labels for every item they return, so resolving them one
 * skill at a time would issue three queries per row. This service batches the
 * assignment, definition, and translation lookups instead.</p>
 */
@Service
public class SkillLabelProjectionService {

    private final SkillLabelService skillLabelService;
    private final LabelDefinitionService labelDefinitionService;
    private final LabelLocalizationService labelLocalizationService;

    public SkillLabelProjectionService(SkillLabelService skillLabelService,
                                       LabelDefinitionService labelDefinitionService,
                                       LabelLocalizationService labelLocalizationService) {
        this.skillLabelService = skillLabelService;
        this.labelDefinitionService = labelDefinitionService;
        this.labelLocalizationService = labelLocalizationService;
    }

    /**
     * Labels for each requested skill, keyed by skill id. Skills without labels are absent
     * from the map rather than mapped to an empty list.
     */
    public Map<Long, List<SkillLabelDto>> labelsBySkillIds(List<Long> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return Map.of();
        }

        List<Long> distinctSkillIds = skillIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (distinctSkillIds.isEmpty()) {
            return Map.of();
        }

        List<SkillLabel> assignments = skillLabelService.listSkillLabelsBySkillIds(distinctSkillIds);
        if (assignments.isEmpty()) {
            return Map.of();
        }

        List<Long> labelIds = assignments.stream()
                .map(SkillLabel::getLabelId)
                .distinct()
                .toList();
        Map<Long, LabelDefinition> definitionsById = labelDefinitionService.listByIds(labelIds).stream()
                .collect(Collectors.toMap(LabelDefinition::getId, Function.identity()));
        Map<Long, List<LabelTranslation>> translationsByLabelId =
                labelDefinitionService.listTranslationsByLabelIds(labelIds);

        return assignments.stream()
                .filter(assignment -> definitionsById.containsKey(assignment.getLabelId()))
                .collect(Collectors.groupingBy(
                        SkillLabel::getSkillId,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                skillAssignments -> skillAssignments.stream()
                                        .map(assignment -> toDto(
                                                definitionsById.get(assignment.getLabelId()),
                                                translationsByLabelId))
                                        .sorted(Comparator.comparing(SkillLabelDto::type)
                                                .thenComparing(SkillLabelDto::slug))
                                        .toList())));
    }

    private SkillLabelDto toDto(LabelDefinition definition,
                                Map<Long, List<LabelTranslation>> translationsByLabelId) {
        return new SkillLabelDto(
                definition.getSlug(),
                definition.getType().name(),
                labelLocalizationService.resolveDisplayName(
                        definition.getSlug(),
                        translationsByLabelId.getOrDefault(definition.getId(), List.of()))
        );
    }
}
