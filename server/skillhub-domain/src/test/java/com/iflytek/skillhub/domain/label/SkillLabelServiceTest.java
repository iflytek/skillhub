package com.iflytek.skillhub.domain.label;

import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

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

    @ParameterizedTest(name = "{0} attach {4}: allowed={5}")
    @MethodSource("skillLabelPermissions")
    void attachUsesActualPermissionChecker(String role, String userId,
                                           Map<Long, NamespaceRole> namespaceRoles,
                                           Set<String> platformRoles, LabelType type,
                                           boolean allowed) throws Exception {
        SkillLabelService service = serviceWithRealChecker(type);
        if (!allowed) {
            assertThrows(DomainForbiddenException.class, () -> service.attachLabel(
                    10L, "automation", userId, namespaceRoles, platformRoles));
            verifyNoInteractions(skillLabelRepository);
            return;
        }
        when(skillLabelRepository.findBySkillId(10L)).thenReturn(List.of());
        when(skillLabelRepository.findBySkillIdAndLabelId(10L, 20L)).thenReturn(Optional.empty());
        when(skillLabelRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SkillLabel attached = service.attachLabel(10L, "automation", userId, namespaceRoles, platformRoles);

        assertEquals(10L, attached.getSkillId());
        assertEquals(20L, attached.getLabelId());
        assertEquals(userId, attached.getCreatedBy());
        verify(skillLabelRepository).save(attached);
    }

    @ParameterizedTest(name = "{0} detach {4}: allowed={5}")
    @MethodSource("skillLabelPermissions")
    void detachUsesActualPermissionChecker(String role, String userId,
                                           Map<Long, NamespaceRole> namespaceRoles,
                                           Set<String> platformRoles, LabelType type,
                                           boolean allowed) throws Exception {
        SkillLabelService service = serviceWithRealChecker(type);
        if (!allowed) {
            assertThrows(DomainForbiddenException.class, () -> service.detachLabel(
                    10L, "automation", userId, namespaceRoles, platformRoles));
            verifyNoInteractions(skillLabelRepository);
            return;
        }
        SkillLabel attached = new SkillLabel(10L, 20L, "original-author");
        when(skillLabelRepository.findBySkillIdAndLabelId(10L, 20L)).thenReturn(Optional.of(attached));

        service.detachLabel(10L, "automation", userId, namespaceRoles, platformRoles);

        verify(skillLabelRepository).delete(attached);
    }

    private SkillLabelService serviceWithRealChecker(LabelType type) throws Exception {
        Skill skill = new Skill(1L, "demo", "author", SkillVisibility.PUBLIC);
        LabelDefinition label = new LabelDefinition("automation", type, true, 0, "creator");
        setId(skill, 10L);
        setId(label, 20L);
        when(skillRepository.findById(10L)).thenReturn(Optional.of(skill));
        when(labelDefinitionRepository.findBySlugIgnoreCase("automation")).thenReturn(Optional.of(label));
        return new SkillLabelService(skillRepository, labelDefinitionRepository, skillLabelRepository,
                new LabelPermissionChecker(org.mockito.Mockito.mock(com.iflytek.skillhub.domain.skill.SkillOwnerAccessPolicy.class)), 10);
    }

    private static Stream<Arguments> skillLabelPermissions() {
        return Stream.of(LabelType.RECOMMENDED, LabelType.PRIVILEGED).flatMap(type -> Stream.of(
                Arguments.of("skill owner", "author", Map.of(1L, NamespaceRole.MEMBER), Set.of(), type,
                        type == LabelType.RECOMMENDED),
                Arguments.of("namespace admin", "admin", Map.of(1L, NamespaceRole.ADMIN), Set.of(), type,
                        type == LabelType.RECOMMENDED),
                Arguments.of("namespace owner", "ns-owner", Map.of(1L, NamespaceRole.OWNER), Set.of(), type,
                        type == LabelType.RECOMMENDED),
                Arguments.of("other namespace admin", "other-admin", Map.of(2L, NamespaceRole.ADMIN), Set.of(), type, false),
                Arguments.of("ordinary member", "member", Map.of(1L, NamespaceRole.MEMBER), Set.of(), type, false),
                Arguments.of("platform skill admin", "skill-admin", Map.of(), Set.of("SKILL_ADMIN"), type, false),
                Arguments.of("super admin", "super-admin", Map.of(), Set.of("SUPER_ADMIN"), type, true)));
    }

    private void setId(Object entity, Long id) throws Exception {
        var field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

}
