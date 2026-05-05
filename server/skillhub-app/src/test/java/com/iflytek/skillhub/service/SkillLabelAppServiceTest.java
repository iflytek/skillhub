package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.label.LabelDefinition;
import com.iflytek.skillhub.domain.label.LabelDefinitionService;
import com.iflytek.skillhub.domain.label.LabelType;
import com.iflytek.skillhub.domain.label.LabelTranslation;
import com.iflytek.skillhub.domain.label.SkillLabel;
import com.iflytek.skillhub.domain.label.SkillLabelService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
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
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void listSkillLabelsBySkillId_shouldReturnDtos() throws Exception {
        LabelDefinition def = new LabelDefinition("tag", LabelType.RECOMMENDED, true, 0, "user");
        setId(def, 5L);
        SkillLabel skillLabel = new SkillLabel(1L, 5L, "user-1");
        setId(skillLabel, 100L);

        when(labelDefinitionService.listByIds(List.of(5L))).thenReturn(List.of(def));
        when(labelDefinitionService.listTranslationsByLabelIds(List.of(5L))).thenReturn(Map.of());
        when(skillLabelService.listSkillLabels(1L)).thenReturn(List.of(skillLabel));
        when(labelLocalizationService.resolveDisplayName("tag", List.of())).thenReturn("Tag");

        var result = service.listSkillLabelsBySkillId(1L);

        assertEquals(1, result.size());
        assertEquals("tag", result.get(0).slug());
    }

    @Test
    void listSkillLabelsBySkillId_returnsEmptyWhenNoLabels() {
        when(skillLabelService.listSkillLabels(99L)).thenReturn(List.of());

        var result = service.listSkillLabelsBySkillId(99L);

        assertEquals(List.of(), result);
    }

    @Test
    void attachLabel_fullFlow() throws Exception {
        Namespace namespace = new Namespace("team", "Team", "owner");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, "my-skill", "user-1", SkillVisibility.PUBLIC);
        setId(skill, 10L);
        SkillLabel attached = new SkillLabel(10L, 5L, "user-1");
        setId(attached, 100L);
        LabelDefinition def = new LabelDefinition("tag", LabelType.RECOMMENDED, true, 0, "user");
        setId(def, 5L);

        when(namespaceRepository.findBySlug("team")).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, "my-skill")).thenReturn(List.of(skill));
        when(skillLabelService.attachLabel(10L, "tag", "user-1", Map.of(), Set.of())).thenReturn(attached);
        when(labelDefinitionService.listByIds(List.of(5L))).thenReturn(List.of(def));
        when(labelDefinitionService.listTranslationsByLabelIds(List.of(5L))).thenReturn(Map.of());
        when(labelLocalizationService.resolveDisplayName("tag", List.of())).thenReturn("Tag");

        var result = service.attachLabel("team", "my-skill", "tag", "user-1", null,
                new AuditRequestContext("127.0.0.1", "JUnit"));

        assertEquals("tag", result.slug());
        verify(labelSearchSyncService).rebuildSkill(10L);
        verify(auditLogService).record("user-1", "SKILL_LABEL_ATTACH", "SKILL", 10L,
                null, "127.0.0.1", "JUnit", "{\"labelSlug\":\"tag\"}");
    }

    @Test
    void detachLabel_fullFlow() throws Exception {
        Namespace namespace = new Namespace("team", "Team", "owner");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, "my-skill", "user-1", SkillVisibility.PUBLIC);
        setId(skill, 10L);

        when(namespaceRepository.findBySlug("team")).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, "my-skill")).thenReturn(List.of(skill));

        var result = service.detachLabel("team", "my-skill", "tag", "user-1", null, null);

        assertEquals("Label detached", result.message());
        verify(labelSearchSyncService).rebuildSkill(10L);
        verify(auditLogService).record("user-1", "SKILL_LABEL_DETACH", "SKILL", 10L,
                null, null, null, "{\"labelSlug\":\"tag\"}");
    }

    @Test
    void resolveSkill_throwsWhenNamespaceNotFound() {
        when(namespaceRepository.findBySlug("missing")).thenReturn(Optional.empty());

        assertThrows(DomainBadRequestException.class, () -> service.listSkillLabels("missing", "skill", "user-1", Map.of()));
    }

    @Test
    void resolveSkillForRead_allowsSuperAdminRegardlessOfVisibility() throws Exception {
        Namespace namespace = new Namespace("team", "Team", "owner");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, "secret", "user-2", SkillVisibility.PRIVATE);
        setId(skill, 10L);
        skill.setLatestVersionId(100L);

        when(namespaceRepository.findBySlug("team")).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, "secret")).thenReturn(List.of(skill));
        when(rbacService.getUserRoleCodes("admin-1")).thenReturn(Set.of("SUPER_ADMIN"));
        when(skillLabelService.listSkillLabels(10L)).thenReturn(List.of());

        var result = service.listSkillLabels("team", "secret", "admin-1", Map.of());

        assertEquals(List.of(), result);
        verify(visibilityChecker, never()).canAccess(any(), any(), any());
    }

    @Test
    void afterCommit_runsImmediatelyWhenNoSynchronization() {
        AtomicBoolean ran = new AtomicBoolean(false);
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                service, "afterCommit", (Runnable) () -> ran.set(true));
        assertThat(ran.get()).isTrue();
    }

    @Test
    void afterCommit_registersSynchronizationWhenActive() {
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            AtomicBoolean ran = new AtomicBoolean(false);
            org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                    service, "afterCommit", (Runnable) () -> ran.set(true));
            assertThat(ran.get()).isFalse();
            var syncs = org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations();
            assertThat(syncs).hasSize(1);
            syncs.get(0).afterCommit();
            assertThat(ran.get()).isTrue();
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clear();
        }
    }

    private void setId(Object entity, Long id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }
}
