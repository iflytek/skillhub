package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.label.LabelDefinition;
import com.iflytek.skillhub.domain.label.LabelDefinitionService;
import com.iflytek.skillhub.domain.label.LabelType;
import com.iflytek.skillhub.domain.label.SkillSuiteLabel;
import com.iflytek.skillhub.domain.label.SkillSuiteLabelService;
import com.iflytek.skillhub.dto.SkillLabelDto;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SkillSuiteLabelProjectionServiceTest {

    private final SkillSuiteLabelService suiteLabelService = mock(SkillSuiteLabelService.class);
    private final LabelDefinitionService labelDefinitionService = mock(LabelDefinitionService.class);
    private final SkillSuiteLabelProjectionService service = new SkillSuiteLabelProjectionService(
            suiteLabelService, labelDefinitionService, new LabelLocalizationService());

    @Test
    void projectsWholeSuitePageWithThreeBoundedLookups() {
        LabelDefinition automation = definition(10L, "automation", LabelType.RECOMMENDED);
        LabelDefinition verified = definition(11L, "verified", LabelType.PRIVILEGED);
        when(suiteLabelService.listSuiteLabelsBySuiteIds(List.of(1L, 2L))).thenReturn(List.of(
                new SkillSuiteLabel(1L, 10L, "owner"),
                new SkillSuiteLabel(1L, 11L, "admin"),
                new SkillSuiteLabel(2L, 10L, "owner")));
        when(labelDefinitionService.listByIds(anyList())).thenReturn(List.of(automation, verified));
        when(labelDefinitionService.listTranslationsByLabelIds(anyList())).thenReturn(Map.of());

        Map<Long, List<SkillLabelDto>> result = service.labelsBySuiteIds(List.of(1L, 2L, 1L));

        assertThat(result.get(1L)).extracting(SkillLabelDto::slug)
                .containsExactly("verified", "automation");
        assertThat(result.get(2L)).extracting(SkillLabelDto::slug)
                .containsExactly("automation");
        verify(suiteLabelService).listSuiteLabelsBySuiteIds(List.of(1L, 2L));
        verify(labelDefinitionService).listByIds(anyList());
        verify(labelDefinitionService).listTranslationsByLabelIds(anyList());
    }

    @Test
    void skipsMissingDefinitionsAndDoesNotQueryForEmptyInput() {
        when(suiteLabelService.listSuiteLabelsBySuiteIds(List.of(1L)))
                .thenReturn(List.of(new SkillSuiteLabel(1L, 99L, "owner")));
        when(labelDefinitionService.listByIds(anyList())).thenReturn(List.of());
        when(labelDefinitionService.listTranslationsByLabelIds(anyList())).thenReturn(Map.of());

        assertThat(service.labelsBySuiteIds(List.of(1L))).isEmpty();
        assertThat(service.labelsBySuiteIds(List.of())).isEmpty();
        assertThat(service.labelsBySuiteIds(null)).isEmpty();
        verify(suiteLabelService, times(1)).listSuiteLabelsBySuiteIds(any());
    }

    private LabelDefinition definition(Long id, String slug, LabelType type) {
        LabelDefinition definition = new LabelDefinition(slug, type, true, 0, "admin");
        ReflectionTestUtils.setField(definition, "id", id);
        return definition;
    }
}
