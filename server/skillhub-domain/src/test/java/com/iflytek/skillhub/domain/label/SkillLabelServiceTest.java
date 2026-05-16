package com.iflytek.skillhub.domain.label;

import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class SkillLabelServiceTest {

    private final SkillRepository skillRepository = mock(SkillRepository.class);
    private final LabelDefinitionRepository labelDefinitionRepository = mock(LabelDefinitionRepository.class);
    private final SkillLabelRepository skillLabelRepository = mock(SkillLabelRepository.class);
    private final LabelPermissionChecker labelPermissionChecker = mock(LabelPermissionChecker.class);

    @Test
    void constructorShouldRejectNonPositivePerSkillLimit() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new SkillLabelService(
                skillRepository,
                labelDefinitionRepository,
                skillLabelRepository,
                labelPermissionChecker,
                0
        ));

        assertEquals("skillhub.label.max-per-skill must be greater than 0", ex.getMessage());
    }

    @Test
    void attachLabelsShouldLoadDefinitionsAndExistingLabelsInBatches() throws Exception {
        SkillLabelService service = new SkillLabelService(
                skillRepository,
                labelDefinitionRepository,
                skillLabelRepository,
                labelPermissionChecker,
                10
        );
        Skill skill = new Skill(1L, "demo", "user-1", SkillVisibility.PUBLIC);
        setId(skill, 10L);
        LabelDefinition official = new LabelDefinition("official", LabelType.RECOMMENDED, true, 1, "admin");
        LabelDefinition featured = new LabelDefinition("featured", LabelType.RECOMMENDED, true, 2, "admin");
        setId(official, 100L);
        setId(featured, 101L);

        when(skillRepository.findById(10L)).thenReturn(Optional.of(skill));
        when(labelDefinitionRepository.findBySlugIn(List.of("official", "featured"))).thenReturn(List.of(official, featured));
        when(labelPermissionChecker.canManageSkillLabel(skill, official, "user-1", Map.of(), Set.of("SUPER_ADMIN"))).thenReturn(true);
        when(labelPermissionChecker.canManageSkillLabel(skill, featured, "user-1", Map.of(), Set.of("SUPER_ADMIN"))).thenReturn(true);
        when(skillLabelRepository.findBySkillId(10L)).thenReturn(List.of());
        when(skillLabelRepository.saveAll(org.mockito.ArgumentMatchers.<Iterable<SkillLabel>>any()))
                .thenAnswer(invocation -> {
                    Iterable<SkillLabel> labels = invocation.getArgument(0);
                    return java.util.stream.StreamSupport.stream(labels.spliterator(), false).toList();
                });

        List<SkillLabel> result = service.attachLabels(
                10L,
                List.of("Official", "featured", "official"),
                "user-1",
                Map.of(),
                Set.of("SUPER_ADMIN")
        );

        assertEquals(List.of(100L, 101L), result.stream().map(SkillLabel::getLabelId).toList());
        verify(labelDefinitionRepository).findBySlugIn(List.of("official", "featured"));
        verify(skillLabelRepository).findBySkillId(10L);
        verify(skillLabelRepository).saveAll(org.mockito.ArgumentMatchers.<Iterable<SkillLabel>>any());
    }

    private void setId(Object entity, Long id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }
}
