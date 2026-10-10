package com.iflytek.skillhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.label.LabelDefinition;
import com.iflytek.skillhub.domain.label.LabelDefinitionService;
import com.iflytek.skillhub.domain.label.LabelType;
import com.iflytek.skillhub.domain.label.SkillLabel;
import com.iflytek.skillhub.domain.label.SkillLabelService;
import com.iflytek.skillhub.dto.SkillLabelDto;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SkillLabelProjectionServiceTest {

    private final SkillLabelService skillLabelService = mock(SkillLabelService.class);
    private final LabelDefinitionService labelDefinitionService = mock(LabelDefinitionService.class);
    private final LabelLocalizationService labelLocalizationService = new LabelLocalizationService();

    private final SkillLabelProjectionService service = new SkillLabelProjectionService(
            skillLabelService, labelDefinitionService, labelLocalizationService);

    @Test
    void labelsBySkillIds_groupsLabelsPerSkillInOneBatch() {
        LabelDefinition automation = definition(10L, "automation", LabelType.RECOMMENDED);
        LabelDefinition audited = definition(11L, "audited", LabelType.PRIVILEGED);

        when(skillLabelService.listSkillLabelsBySkillIds(List.of(1L, 2L))).thenReturn(List.of(
                new SkillLabel(1L, 10L, "owner-1"),
                new SkillLabel(1L, 11L, "owner-1"),
                new SkillLabel(2L, 10L, "owner-2")
        ));
        when(labelDefinitionService.listByIds(anyList())).thenReturn(List.of(automation, audited));
        when(labelDefinitionService.listTranslationsByLabelIds(anyList())).thenReturn(Map.of());

        Map<Long, List<SkillLabelDto>> labels = service.labelsBySkillIds(List.of(1L, 2L));

        // sorted by label type, then slug: PRIVILEGED before RECOMMENDED
        assertEquals(List.of("audited", "automation"), labels.get(1L).stream().map(SkillLabelDto::slug).toList());
        assertEquals(List.of("automation"), labels.get(2L).stream().map(SkillLabelDto::slug).toList());

        // One query per lookup for the whole page, not per skill.
        verify(skillLabelService, times(1)).listSkillLabelsBySkillIds(anyList());
        verify(labelDefinitionService, times(1)).listByIds(anyList());
        verify(labelDefinitionService, times(1)).listTranslationsByLabelIds(anyList());
    }

    @Test
    void labelsBySkillIds_skipsAssignmentsWithoutADefinition() {
        when(skillLabelService.listSkillLabelsBySkillIds(anyList()))
                .thenReturn(List.of(new SkillLabel(1L, 99L, "owner-1")));
        when(labelDefinitionService.listByIds(anyList())).thenReturn(List.of());
        when(labelDefinitionService.listTranslationsByLabelIds(anyList())).thenReturn(Map.of());

        assertTrue(service.labelsBySkillIds(List.of(1L)).isEmpty());
    }

    @Test
    void labelsBySkillIds_touchesNoRepositoryForAnEmptyPage() {
        assertTrue(service.labelsBySkillIds(List.of()).isEmpty());
        assertTrue(service.labelsBySkillIds(null).isEmpty());

        verify(skillLabelService, never()).listSkillLabelsBySkillIds(any());
    }

    private static LabelDefinition definition(Long id, String slug, LabelType type) {
        LabelDefinition definition = new LabelDefinition(slug, type, true, 0, "admin");
        ReflectionTestUtils.setField(definition, "id", id);
        return definition;
    }
}
