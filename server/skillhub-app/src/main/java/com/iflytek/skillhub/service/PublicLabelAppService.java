package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.label.LabelDefinition;
import com.iflytek.skillhub.domain.label.LabelDefinitionService;
import com.iflytek.skillhub.dto.SkillLabelDto;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PublicLabelAppService {

    private final LabelDefinitionService labelDefinitionService;
    private final LabelLocalizationService labelLocalizationService;

    public PublicLabelAppService(LabelDefinitionService labelDefinitionService,
                                 LabelLocalizationService labelLocalizationService) {
        this.labelDefinitionService = labelDefinitionService;
        this.labelLocalizationService = labelLocalizationService;
    }

    public List<SkillLabelDto> listVisibleFilters() {
        List<LabelDefinition> definitions = labelDefinitionService.listVisibleFilters();
        java.util.Map<Long, java.util.List<com.iflytek.skillhub.domain.label.LabelTranslation>> translationsByLabelId =
                labelDefinitionService.listTranslationsByLabelIds(definitions.stream().map(LabelDefinition::getId).toList());
        return definitions.stream()
                .map(labelDefinition -> toDto(labelDefinition, translationsByLabelId))
                .toList();
    }

    private SkillLabelDto toDto(LabelDefinition labelDefinition,
                                java.util.Map<Long, java.util.List<com.iflytek.skillhub.domain.label.LabelTranslation>> translationsByLabelId) {
        return new SkillLabelDto(
                labelDefinition.getSlug(),
                labelDefinition.getType().name(),
                labelLocalizationService.resolveDisplayName(
                        labelDefinition.getSlug(),
                        translationsByLabelId.getOrDefault(labelDefinition.getId(), List.of()))
        );
    }
}
