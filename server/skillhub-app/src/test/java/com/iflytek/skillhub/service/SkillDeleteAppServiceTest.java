package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillHardDeleteService;
import com.iflytek.skillhub.search.SearchIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import org.mockito.InOrder;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SkillDeleteAppServiceTest {

    @Mock
    private SkillRepository skillRepository;
    @Mock
    private SkillHardDeleteService skillHardDeleteService;
    @Mock
    private SearchIndexService searchIndexService;

    private SkillDeleteAppService service;

    @BeforeEach
    void setUp() {
        service = new SkillDeleteAppService(skillRepository, skillHardDeleteService, searchIndexService);
    }

    @Test
    void deleteSkill_deletesExistingSkillAndSearchDocument() {
        Skill skill = new Skill(1L, "demo-skill", "owner-1", SkillVisibility.PUBLIC);
        setField(skill, "id", 11L);
        given(skillRepository.findByNamespaceSlugAndSlug("global", "demo-skill")).willReturn(Optional.of(skill));

        SkillDeleteAppService.DeleteResult result =
                service.deleteSkill("global", "demo-skill", "super-1", new AuditRequestContext("127.0.0.1", "JUnit"));

        assertThat(result.deleted()).isTrue();
        assertThat(result.skillId()).isEqualTo(11L);
        InOrder inOrder = inOrder(searchIndexService, skillHardDeleteService);
        inOrder.verify(searchIndexService).remove(11L);
        inOrder.verify(skillHardDeleteService).hardDeleteSkill(skill, "global", "super-1", "127.0.0.1", "JUnit");
        verify(skillHardDeleteService).hardDeleteSkill(skill, "global", "super-1", "127.0.0.1", "JUnit");
    }

    @Test
    void deleteSkill_isIdempotentWhenSkillDoesNotExist() {
        given(skillRepository.findByNamespaceSlugAndSlug("global", "missing-skill")).willReturn(Optional.empty());

        SkillDeleteAppService.DeleteResult result =
                service.deleteSkill("global", "missing-skill", "super-1", new AuditRequestContext("127.0.0.1", "JUnit"));

        assertThat(result.deleted()).isFalse();
        assertThat(result.skillId()).isNull();
        verify(skillHardDeleteService, never()).hardDeleteSkill(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(searchIndexService, never()).remove(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void deleteSkillFromPortal_allowsOwner() {
        Skill skill = new Skill(1L, "demo-skill", "owner-1", SkillVisibility.PUBLIC);
        setField(skill, "id", 11L);
        given(skillRepository.findByNamespaceSlugAndSlug("global", "demo-skill")).willReturn(Optional.of(skill));

        SkillDeleteAppService.DeleteResult result = service.deleteSkillFromPortal(
                "global",
                "demo-skill",
                new PlatformPrincipal("owner-1", "Owner", "owner@example.com", "", "session", java.util.Set.of("USER")),
                new AuditRequestContext("127.0.0.1", "JUnit")
        );

        assertThat(result.deleted()).isTrue();
        InOrder inOrder = inOrder(searchIndexService, skillHardDeleteService);
        inOrder.verify(searchIndexService).remove(11L);
        inOrder.verify(skillHardDeleteService).hardDeleteSkill(skill, "global", "owner-1", "127.0.0.1", "JUnit");
        verify(skillHardDeleteService).hardDeleteSkill(skill, "global", "owner-1", "127.0.0.1", "JUnit");
    }

    @Test
    void deleteSkillFromPortal_allowsSuperAdmin() {
        Skill skill = new Skill(1L, "demo-skill", "owner-1", SkillVisibility.PUBLIC);
        setField(skill, "id", 11L);
        given(skillRepository.findByNamespaceSlugAndSlug("global", "demo-skill")).willReturn(Optional.of(skill));

        SkillDeleteAppService.DeleteResult result = service.deleteSkillFromPortal(
                "global",
                "demo-skill",
                new PlatformPrincipal("super-1", "Super", "super@example.com", "", "session", java.util.Set.of("SUPER_ADMIN")),
                new AuditRequestContext("127.0.0.1", "JUnit")
        );

        assertThat(result.deleted()).isTrue();
        InOrder inOrder = inOrder(searchIndexService, skillHardDeleteService);
        inOrder.verify(searchIndexService).remove(11L);
        inOrder.verify(skillHardDeleteService).hardDeleteSkill(skill, "global", "super-1", "127.0.0.1", "JUnit");
        verify(skillHardDeleteService).hardDeleteSkill(skill, "global", "super-1", "127.0.0.1", "JUnit");
    }

    @Test
    void deleteSkillFromPortal_rejectsNonOwnerWithoutSuperAdmin() {
        Skill skill = new Skill(1L, "demo-skill", "owner-1", SkillVisibility.PUBLIC);
        setField(skill, "id", 11L);
        given(skillRepository.findByNamespaceSlugAndSlug("global", "demo-skill")).willReturn(Optional.of(skill));

        assertThatThrownBy(() -> service.deleteSkillFromPortal(
                "global",
                "demo-skill",
                new PlatformPrincipal("user-2", "User", "user@example.com", "", "session", java.util.Set.of("USER")),
                new AuditRequestContext("127.0.0.1", "JUnit")
        )).isInstanceOf(DomainForbiddenException.class);
    }

    @Test
    void deleteSkillFromPortal_isIdempotentWhenSkillDoesNotExist() {
        given(skillRepository.findByNamespaceSlugAndSlug("global", "missing-skill")).willReturn(Optional.empty());

        SkillDeleteAppService.DeleteResult result = service.deleteSkillFromPortal(
                "global",
                "missing-skill",
                new PlatformPrincipal("owner-1", "Owner", "owner@example.com", "", "session", java.util.Set.of("USER")),
                new AuditRequestContext("127.0.0.1", "JUnit")
        );

        assertThat(result.deleted()).isFalse();
        assertThat(result.skillId()).isNull();
        verify(skillHardDeleteService, never()).hardDeleteSkill(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
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
