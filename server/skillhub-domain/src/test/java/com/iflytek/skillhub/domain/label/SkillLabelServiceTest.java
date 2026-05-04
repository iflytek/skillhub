package com.iflytek.skillhub.domain.label;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillLabelServiceTest {

    private final SkillRepository skillRepository = mock(SkillRepository.class);
    private final LabelDefinitionRepository labelDefinitionRepository = mock(LabelDefinitionRepository.class);
    private final SkillLabelRepository skillLabelRepository = mock(SkillLabelRepository.class);
    private final LabelPermissionChecker labelPermissionChecker = mock(LabelPermissionChecker.class);

    private final SkillLabelService service = new SkillLabelService(
            skillRepository,
            labelDefinitionRepository,
            skillLabelRepository,
            labelPermissionChecker,
            10
    );

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
    void constructorShouldAcceptPositiveLimit() {
        SkillLabelService s = new SkillLabelService(skillRepository, labelDefinitionRepository, skillLabelRepository, labelPermissionChecker, 5);
        assertEquals(5, s.getClass().getDeclaredFields().length);
    }

    @Test
    void listSkillLabelsShouldDelegateToRepository() {
        List<SkillLabel> expected = List.of(new SkillLabel(1L, 2L, "user"));
        when(skillLabelRepository.findBySkillId(1L)).thenReturn(expected);

        assertEquals(expected, service.listSkillLabels(1L));
    }

    @Test
    void listByLabelIdShouldDelegateToRepository() {
        List<SkillLabel> expected = List.of(new SkillLabel(1L, 2L, "user"));
        when(skillLabelRepository.findByLabelId(2L)).thenReturn(expected);

        assertEquals(expected, service.listByLabelId(2L));
    }

    @Test
    void attachLabelShouldSucceedWhenValid() {
        Skill skill = new Skill(1L, "demo", "owner", SkillVisibility.PUBLIC);
        setField(skill, "id", 10L);
        LabelDefinition label = new LabelDefinition("official", LabelType.RECOMMENDED, true, 0, "admin");
        setField(label, "id", 20L);
        SkillLabel saved = new SkillLabel(10L, 20L, "owner");

        when(skillRepository.findById(10L)).thenReturn(Optional.of(skill));
        when(labelDefinitionRepository.findBySlugIgnoreCase("official")).thenReturn(Optional.of(label));
        when(labelPermissionChecker.canManageSkillLabel(skill, label, "owner", Map.of(), Set.of())).thenReturn(true);
        when(skillLabelRepository.findBySkillId(10L)).thenReturn(List.of());
        when(skillLabelRepository.findBySkillIdAndLabelId(10L, 20L)).thenReturn(Optional.empty());
        when(skillLabelRepository.save(any(SkillLabel.class))).thenReturn(saved);

        SkillLabel result = service.attachLabel(10L, "official", "owner", Map.of(), Set.of());
        assertEquals(saved, result);
    }

    @Test
    void attachLabelShouldReturnExistingWhenAlreadyAttached() {
        Skill skill = new Skill(1L, "demo", "owner", SkillVisibility.PUBLIC);
        setField(skill, "id", 10L);
        LabelDefinition label = new LabelDefinition("official", LabelType.RECOMMENDED, true, 0, "admin");
        setField(label, "id", 20L);
        SkillLabel existing = new SkillLabel(10L, 20L, "owner");

        when(skillRepository.findById(10L)).thenReturn(Optional.of(skill));
        when(labelDefinitionRepository.findBySlugIgnoreCase("official")).thenReturn(Optional.of(label));
        when(labelPermissionChecker.canManageSkillLabel(skill, label, "owner", Map.of(), Set.of())).thenReturn(true);
        when(skillLabelRepository.findBySkillId(10L)).thenReturn(List.of());
        when(skillLabelRepository.findBySkillIdAndLabelId(10L, 20L)).thenReturn(Optional.of(existing));

        SkillLabel result = service.attachLabel(10L, "official", "owner", Map.of(), Set.of());
        assertEquals(existing, result);
        verify(skillLabelRepository, never()).save(any());
    }

    @Test
    void attachLabelShouldThrowWhenMaxLabelsReached() {
        Skill skill = new Skill(1L, "demo", "owner", SkillVisibility.PUBLIC);
        setField(skill, "id", 10L);
        LabelDefinition label = new LabelDefinition("official", LabelType.RECOMMENDED, true, 0, "admin");
        setField(label, "id", 20L);

        when(skillRepository.findById(10L)).thenReturn(Optional.of(skill));
        when(labelDefinitionRepository.findBySlugIgnoreCase("official")).thenReturn(Optional.of(label));
        when(labelPermissionChecker.canManageSkillLabel(skill, label, "owner", Map.of(), Set.of())).thenReturn(true);
        when(skillLabelRepository.findBySkillId(10L)).thenReturn(List.of(
                new SkillLabel(10L, 1L, "owner"), new SkillLabel(10L, 2L, "owner"),
                new SkillLabel(10L, 3L, "owner"), new SkillLabel(10L, 4L, "owner"),
                new SkillLabel(10L, 5L, "owner"), new SkillLabel(10L, 6L, "owner"),
                new SkillLabel(10L, 7L, "owner"), new SkillLabel(10L, 8L, "owner"),
                new SkillLabel(10L, 9L, "owner"), new SkillLabel(10L, 10L, "owner")
        ));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.attachLabel(10L, "official", "owner", Map.of(), Set.of()));
        assertEquals("label.skill.too_many", ex.messageCode());
    }

    @Test
    void attachLabelShouldThrowWhenNoPermission() {
        Skill skill = new Skill(1L, "demo", "owner", SkillVisibility.PUBLIC);
        setField(skill, "id", 10L);
        LabelDefinition label = new LabelDefinition("official", LabelType.RECOMMENDED, true, 0, "admin");
        setField(label, "id", 20L);

        when(skillRepository.findById(10L)).thenReturn(Optional.of(skill));
        when(labelDefinitionRepository.findBySlugIgnoreCase("official")).thenReturn(Optional.of(label));
        when(labelPermissionChecker.canManageSkillLabel(skill, label, "owner", Map.of(), Set.of())).thenReturn(false);

        DomainForbiddenException ex = assertThrows(DomainForbiddenException.class,
                () -> service.attachLabel(10L, "official", "owner", Map.of(), Set.of()));
        assertEquals("label.skill.no_permission", ex.messageCode());
    }

    @Test
    void detachLabelShouldSucceedWhenValid() {
        Skill skill = new Skill(1L, "demo", "owner", SkillVisibility.PUBLIC);
        setField(skill, "id", 10L);
        LabelDefinition label = new LabelDefinition("official", LabelType.RECOMMENDED, true, 0, "admin");
        setField(label, "id", 20L);
        SkillLabel existing = new SkillLabel(10L, 20L, "owner");

        when(skillRepository.findById(10L)).thenReturn(Optional.of(skill));
        when(labelDefinitionRepository.findBySlugIgnoreCase("official")).thenReturn(Optional.of(label));
        when(labelPermissionChecker.canManageSkillLabel(skill, label, "owner", Map.of(), Set.of())).thenReturn(true);
        when(skillLabelRepository.findBySkillIdAndLabelId(10L, 20L)).thenReturn(Optional.of(existing));

        service.detachLabel(10L, "official", "owner", Map.of(), Set.of());
        verify(skillLabelRepository).delete(existing);
    }

    @Test
    void detachLabelShouldThrowWhenNotFound() {
        Skill skill = new Skill(1L, "demo", "owner", SkillVisibility.PUBLIC);
        setField(skill, "id", 10L);
        LabelDefinition label = new LabelDefinition("official", LabelType.RECOMMENDED, true, 0, "admin");
        setField(label, "id", 20L);

        when(skillRepository.findById(10L)).thenReturn(Optional.of(skill));
        when(labelDefinitionRepository.findBySlugIgnoreCase("official")).thenReturn(Optional.of(label));
        when(labelPermissionChecker.canManageSkillLabel(skill, label, "owner", Map.of(), Set.of())).thenReturn(true);
        when(skillLabelRepository.findBySkillIdAndLabelId(10L, 20L)).thenReturn(Optional.empty());

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.detachLabel(10L, "official", "owner", Map.of(), Set.of()));
        assertEquals("label.skill.not_found", ex.messageCode());
    }

    @Test
    void detachLabelShouldThrowWhenNoPermission() {
        Skill skill = new Skill(1L, "demo", "owner", SkillVisibility.PUBLIC);
        setField(skill, "id", 10L);
        LabelDefinition label = new LabelDefinition("official", LabelType.RECOMMENDED, true, 0, "admin");
        setField(label, "id", 20L);

        when(skillRepository.findById(10L)).thenReturn(Optional.of(skill));
        when(labelDefinitionRepository.findBySlugIgnoreCase("official")).thenReturn(Optional.of(label));
        when(labelPermissionChecker.canManageSkillLabel(skill, label, "owner", Map.of(), Set.of())).thenReturn(false);

        DomainForbiddenException ex = assertThrows(DomainForbiddenException.class,
                () -> service.detachLabel(10L, "official", "owner", Map.of(), Set.of()));
        assertEquals("label.skill.no_permission", ex.messageCode());
    }

    @Test
    void findSkillShouldThrowWhenNotFound() {
        when(skillRepository.findById(99L)).thenReturn(Optional.empty());

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.attachLabel(99L, "official", "owner", Map.of(), Set.of()));
        assertEquals("error.skill.notFound", ex.messageCode());
    }

    @Test
    void findLabelShouldThrowWhenNotFound() {
        Skill skill = new Skill(1L, "demo", "owner", SkillVisibility.PUBLIC);
        setField(skill, "id", 10L);

        when(skillRepository.findById(10L)).thenReturn(Optional.of(skill));
        when(labelDefinitionRepository.findBySlugIgnoreCase("missing")).thenReturn(Optional.empty());

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.attachLabel(10L, "missing", "owner", Map.of(), Set.of()));
        assertEquals("label.not_found", ex.messageCode());
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
