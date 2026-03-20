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
        return labelDefinitionService.listVisibleFilters().stream()
                .map(this::toDto)
                .toList();
    }

    private SkillLabelDto toDto(LabelDefinition labelDefinition) {
        return new SkillLabelDto(
                labelDefinition.getSlug(),
                labelDefinition.getType().name(),
                labelLocalizationService.resolveDisplayName(
                        labelDefinition.getSlug(),
                        labelDefinitionService.listTranslations(labelDefinition.getId()))
        );
    }
}
