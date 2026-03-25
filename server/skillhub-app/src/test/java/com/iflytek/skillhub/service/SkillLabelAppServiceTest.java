package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.label.LabelDefinitionService;
import com.iflytek.skillhub.domain.label.LabelTranslation;
import com.iflytek.skillhub.domain.label.SkillLabel;
import com.iflytek.skillhub.domain.label.SkillLabelService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.skill.service.SkillSlugResolutionService;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillLabelAppServiceTest {

    @Mock
    private NamespaceRepository namespaceRepository;
    @Mock
    private SkillRepository skillRepository;
    @Mock
    private VisibilityChecker visibilityChecker;
    @Mock
    private LabelDefinitionService labelDefinitionService;
    @Mock
    private SkillLabelService skillLabelService;
    @Mock
    private LabelLocalizationService labelLocalizationService;
    @Mock
    private RbacService rbacService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private LabelSearchSyncService labelSearchSyncService;

    private SkillLabelAppService service;
    private SkillSlugResolutionService skillSlugResolutionService;

    @BeforeEach
    void setUp() {
        skillSlugResolutionService = new SkillSlugResolutionService(skillRepository);
        service = new SkillLabelAppService(
                namespaceRepository,
                skillRepository,
                visibilityChecker,
                labelDefinitionService,
                skillLabelService,
                labelLocalizationService,
                rbacService,
                auditLogService,
                labelSearchSyncService,
                skillSlugResolutionService
        );
    }

    @Test
    void listSkillLabels_shouldPreferCurrentUsersOwnSkillWhenSlugIsDuplicated() throws Exception {
        Namespace namespace = new Namespace("global", "Global", "ns-owner");
        setId(namespace, 1L);

        Skill otherUsersSkill = new Skill(1L, "brainstorming", "user-2", SkillVisibility.PRIVATE);
        setId(otherUsersSkill, 10L);
        Skill ownersSkill = new Skill(1L, "brainstorming", "user-1", SkillVisibility.PRIVATE);
        setId(ownersSkill, 11L);

        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, "brainstorming")).thenReturn(List.of(otherUsersSkill, ownersSkill));
        when(rbacService.getUserRoleCodes("user-1")).thenReturn(Set.of());
        when(visibilityChecker.canAccess(ownersSkill, "user-1", Map.of())).thenReturn(true);
        when(skillLabelService.listSkillLabels(11L)).thenReturn(List.of());

        List<com.iflytek.skillhub.dto.SkillLabelDto> result = service.listSkillLabels(
                "global",
                "brainstorming",
                "user-1",
                Map.of()
        );

        assertEquals(List.of(), result);
        verify(skillLabelService).listSkillLabels(11L);
        verify(visibilityChecker, never()).canAccess(otherUsersSkill, "user-1", Map.of());
    }

    @Test
    void listSkillLabels_shouldStillRejectWhenResolvedSkillIsActuallyNotAccessible() throws Exception {
        Namespace namespace = new Namespace("global", "Global", "ns-owner");
        setId(namespace, 1L);

        Skill inaccessibleSkill = new Skill(1L, "brainstorming", "user-2", SkillVisibility.PRIVATE);
        setId(inaccessibleSkill, 10L);
        inaccessibleSkill.setLatestVersionId(10L);

        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, "brainstorming")).thenReturn(List.of(inaccessibleSkill));
        when(rbacService.getUserRoleCodes("user-1")).thenReturn(Set.of());
        when(visibilityChecker.canAccess(inaccessibleSkill, "user-1", Map.of())).thenReturn(false);

        assertThrows(DomainForbiddenException.class, () -> service.listSkillLabels(
                "global",
                "brainstorming",
                "user-1",
                Map.of()
        ));
    }

    private void setId(Object entity, Long id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }
}
