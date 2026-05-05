package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.label.LabelDefinition;
import com.iflytek.skillhub.domain.label.LabelDefinitionService;
import com.iflytek.skillhub.domain.label.LabelTranslation;
import com.iflytek.skillhub.domain.label.LabelType;
import com.iflytek.skillhub.dto.SkillLabelDto;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PublicLabelAppServiceTest {

    private final LabelDefinitionService labelDefinitionService = mock(LabelDefinitionService.class);
    private final LabelLocalizationService labelLocalizationService = mock(LabelLocalizationService.class);
    private final PublicLabelAppService service = new PublicLabelAppService(
            labelDefinitionService, labelLocalizationService
    );

    @Test
    void listVisibleFilters_returnsMappedDtos() {
        LabelDefinition def1 = new LabelDefinition("category", LabelType.RECOMMENDED, true, 0, "user");
        org.springframework.test.util.ReflectionTestUtils.setField(def1, "id", 1L);
        LabelDefinition def2 = new LabelDefinition("status", LabelType.PRIVILEGED, true, 1, "user");
        org.springframework.test.util.ReflectionTestUtils.setField(def2, "id", 2L);

        when(labelDefinitionService.listVisibleFilters()).thenReturn(List.of(def1, def2));
        when(labelDefinitionService.listTranslationsByLabelIds(List.of(1L, 2L)))
                .thenReturn(Map.of(
                        1L, List.of(new LabelTranslation(1L, "en", "Category")),
                        2L, List.of(new LabelTranslation(2L, "en", "Status"))
                ));
        when(labelLocalizationService.resolveDisplayName(eq("category"), any()))
                .thenReturn("Category");
        when(labelLocalizationService.resolveDisplayName(eq("status"), any()))
                .thenReturn("Status");

        List<SkillLabelDto> result = service.listVisibleFilters();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).slug()).isEqualTo("category");
        assertThat(result.get(0).displayName()).isEqualTo("Category");
    }

    @Test
    void listVisibleFilters_usesEmptyTranslationsWhenNoneAvailable() {
        LabelDefinition def1 = new LabelDefinition("type", LabelType.RECOMMENDED, true, 0, "user");
        org.springframework.test.util.ReflectionTestUtils.setField(def1, "id", 1L);

        when(labelDefinitionService.listVisibleFilters()).thenReturn(List.of(def1));
        when(labelDefinitionService.listTranslationsByLabelIds(List.of(1L)))
                .thenReturn(Map.of());
        when(labelLocalizationService.resolveDisplayName(eq("type"), any()))
                .thenReturn("type");

        List<SkillLabelDto> result = service.listVisibleFilters();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).displayName()).isEqualTo("type");
    }
}
