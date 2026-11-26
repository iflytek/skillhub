package com.iflytek.skillhub.service;

import java.util.Optional;
import java.util.Set;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillHardDeleteService;
import com.iflytek.skillhub.search.SearchIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import org.mockito.InOrder;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SkillDeleteAppServiceTest {
    @Mock private com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository members;

    @Mock
    private SkillRepository skillRepository;
    @Mock
    private SkillHardDeleteService skillHardDeleteService;
    @Mock
    private SearchIndexService searchIndexService;
    @Mock
    private NamespaceRepository namespaceRepository;

    private SkillDeleteAppService service;

    @BeforeEach
    void setUp() {
        service = new SkillDeleteAppService(org.mockito.Mockito.mock(com.iflytek.skillhub.domain.skill.SkillOwnerAccessPolicy.class), skillRepository, namespaceRepository, members, skillHardDeleteService, searchIndexService);
    }

    @Test
    void deleteSkill_deletesExistingSkillAndSearchDocument() {
        Skill skill = new Skill(1L, "demo-skill", "owner-1", SkillVisibility.PUBLIC);
        setField(skill, "id", 11L);
        given(skillRepository.findByNamespaceSlugAndSlug("global", "demo-skill")).willReturn(List.of(skill));

        SkillDeleteAppService.DeleteResult result =
                service.deleteSkill("global", "demo-skill", null, "super-1", new AuditRequestContext("127.0.0.1", "JUnit"));

        assertThat(result.deleted()).isTrue();
        assertThat(result.skillId()).isEqualTo(11L);
        InOrder inOrder = inOrder(searchIndexService, skillHardDeleteService);
        inOrder.verify(searchIndexService).remove(11L);
        inOrder.verify(skillHardDeleteService).hardDeleteSkill(skill, "global", "super-1", "127.0.0.1", "JUnit");
        verify(skillHardDeleteService).hardDeleteSkill(skill, "global", "super-1", "127.0.0.1", "JUnit");
    }

    @Test
    void deleteSkill_isIdempotentWhenSkillDoesNotExist() {
        given(skillRepository.findByNamespaceSlugAndSlug("global", "missing-skill")).willReturn(List.of());

        SkillDeleteAppService.DeleteResult result =
                service.deleteSkill("global", "missing-skill", null, "super-1", new AuditRequestContext("127.0.0.1", "JUnit"));

        assertThat(result.deleted()).isFalse();
        assertThat(result.skillId()).isNull();
        verify(skillHardDeleteService, never()).hardDeleteSkill(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(searchIndexService, never()).remove(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void deleteSkillFromPortal_allowsOwner() {
        Skill skill = new Skill(1L, "demo-skill", "owner-1", SkillVisibility.PUBLIC);
        setField(skill, "id", 11L);
        given(skillRepository.findByNamespaceSlugAndSlug("global", "demo-skill")).willReturn(List.of(skill));

        SkillDeleteAppService.DeleteResult result = service.deleteSkillFromPortal(
                "global",
                "demo-skill",
                null,
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
        given(skillRepository.findByNamespaceSlugAndSlug("global", "demo-skill")).willReturn(List.of(skill));

        SkillDeleteAppService.DeleteResult result = service.deleteSkillFromPortal(
                "global",
                "demo-skill",
                null,
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
    void deleteSkillFromPortal_resolvesCurrentOwnerWhenSlugMatchesMultipleSkills() {
        Skill otherOwnersSkill = new Skill(1L, "demo-skill", "owner-2", SkillVisibility.PUBLIC);
        setField(otherOwnersSkill, "id", 12L);
        Skill currentOwnersSkill = new Skill(1L, "demo-skill", "owner-1", SkillVisibility.PUBLIC);
        setField(currentOwnersSkill, "id", 11L);
        given(skillRepository.findByNamespaceSlugAndSlug("global", "demo-skill"))
                .willReturn(List.of(otherOwnersSkill, currentOwnersSkill));

        SkillDeleteAppService.DeleteResult result = service.deleteSkillFromPortal(
                "global",
                "demo-skill",
                null,
                new PlatformPrincipal("owner-1", "Owner", "owner@example.com", "", "session", java.util.Set.of("USER")),
                new AuditRequestContext("127.0.0.1", "JUnit")
        );

        assertThat(result.deleted()).isTrue();
        assertThat(result.skillId()).isEqualTo(11L);
        InOrder inOrder = inOrder(searchIndexService, skillHardDeleteService);
        inOrder.verify(searchIndexService).remove(11L);
        inOrder.verify(skillHardDeleteService).hardDeleteSkill(currentOwnersSkill, "global", "owner-1", "127.0.0.1", "JUnit");
    }

    @Test
    void deleteSkillFromPortal_rejectsNonOwnerWithoutSuperAdmin() {
        Skill skill = new Skill(1L, "demo-skill", "owner-1", SkillVisibility.PUBLIC);
        setField(skill, "id", 11L);
        given(skillRepository.findByNamespaceSlugAndSlug("global", "demo-skill")).willReturn(List.of(skill));

        assertThatThrownBy(() -> service.deleteSkillFromPortal(
                "global",
                "demo-skill",
                null,
                new PlatformPrincipal("user-2", "User", "user@example.com", "", "session", java.util.Set.of("USER")),
                new AuditRequestContext("127.0.0.1", "JUnit")
        )).isInstanceOf(DomainForbiddenException.class);
    }

    @Test
    void deleteSkillFromPortal_isIdempotentWhenSkillDoesNotExist() {
        given(skillRepository.findByNamespaceSlugAndSlug("global", "missing-skill")).willReturn(List.of());

        SkillDeleteAppService.DeleteResult result = service.deleteSkillFromPortal(
                "global",
                "missing-skill",
                null,
                new PlatformPrincipal("owner-1", "Owner", "owner@example.com", "", "session", java.util.Set.of("USER")),
                new AuditRequestContext("127.0.0.1", "JUnit")
        );

        assertThat(result.deleted()).isFalse();
        assertThat(result.skillId()).isNull();
        verify(skillHardDeleteService, never()).hardDeleteSkill(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteSkillById_deletesExistingSkillAndSearchDocument() {
        Skill skill = new Skill(1L, "demo-skill", "owner-1", SkillVisibility.PUBLIC);
        setField(skill, "id", 11L);
        Namespace namespace = new Namespace("global", "Global", "system");
        setField(namespace, "id", 1L);
        given(skillRepository.findById(11L)).willReturn(java.util.Optional.of(skill));
        given(namespaceRepository.findById(1L)).willReturn(java.util.Optional.of(namespace));

        SkillDeleteAppService.DeleteResult result =
                service.deleteSkillById(11L, "super-1", new AuditRequestContext("127.0.0.1", "JUnit"));

        assertThat(result.deleted()).isTrue();
        assertThat(result.skillId()).isEqualTo(11L);
        assertThat(result.namespace()).isEqualTo("global");
        assertThat(result.slug()).isEqualTo("demo-skill");
        InOrder inOrder = inOrder(searchIndexService, skillHardDeleteService);
        inOrder.verify(searchIndexService).remove(11L);
        inOrder.verify(skillHardDeleteService).hardDeleteSkill(skill, "global", "super-1", "127.0.0.1", "JUnit");
    }

    @Test
    void deleteSkillById_rejectsMissingSkill() {
        given(skillRepository.findById(11L)).willReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.deleteSkillById(11L, "super-1", new AuditRequestContext("127.0.0.1", "JUnit")))
                .isInstanceOf(DomainNotFoundException.class);
    }

    @Test
    void tokenCannotDeleteTeamSkillAfterAuthorLeavesEvenWhenSkillAdmin() {
        var ns = new Namespace("team-a", "Team A", "author");
        ns.setType(com.iflytek.skillhub.domain.namespace.NamespaceType.TEAM);
        var skill = new Skill(7L, "demo", "author", SkillVisibility.NAMESPACE_ONLY);
        org.mockito.Mockito.when(skillRepository.findById(11L)).thenReturn(Optional.of(skill));
        org.mockito.Mockito.when(namespaceRepository.findById(7L)).thenReturn(Optional.of(ns));
        org.mockito.Mockito.when(namespaceRepository.findLockedById(7L)).thenReturn(Optional.of(ns));
        var principal = new PlatformPrincipal("author", "Author", null, null, "api_token", Set.of("SKILL_ADMIN"));
        assertThatThrownBy(() -> service.deleteSkillByIdFromPortal(11L, principal, null))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException.class);
        org.mockito.Mockito.verifyNoInteractions(skillHardDeleteService);
    }

    @Test
    void tokenCannotDeleteTeamSkillByCoordinateAfterAuthorLeaves() {
        var namespace = new Namespace("team-a", "Team A", "author");
        namespace.setType(com.iflytek.skillhub.domain.namespace.NamespaceType.TEAM);
        var skill = new Skill(7L, "demo", "author", SkillVisibility.NAMESPACE_ONLY);
        setField(skill, "id", 11L);
        given(skillRepository.findByNamespaceSlugAndSlug("team-a", "demo")).willReturn(List.of(skill));
        given(namespaceRepository.findById(7L)).willReturn(Optional.of(namespace));
        given(namespaceRepository.findLockedById(7L)).willReturn(Optional.of(namespace));
        var principal = new PlatformPrincipal("author", "Author", null, null, "api_token", Set.of("SKILL_ADMIN"));

        assertThatThrownBy(() -> service.deleteSkillFromPortal("team-a", "demo", null, principal, null))
                .isInstanceOf(DomainForbiddenException.class);

        org.mockito.Mockito.verifyNoInteractions(searchIndexService, skillHardDeleteService);
    }

    @ParameterizedTest
    @CsvSource({
            "id, api_token, USER, TEAM, true",
            "coordinate, api_token, USER, TEAM, true",
            "id, api_token, SUPER_ADMIN, TEAM, false",
            "coordinate, api_token, SUPER_ADMIN, TEAM, false",
            "id, session, SKILL_ADMIN, TEAM, false",
            "coordinate, session, SKILL_ADMIN, TEAM, false",
            "id, api_token, USER, GLOBAL, false",
            "coordinate, api_token, USER, GLOBAL, false"
    })
    void portalDeletePreservesAllowedAuthorsAndAdministrators(String path, String provider, String role,
                                                              String namespaceType, boolean currentMember) {
        var namespace = new Namespace("team-a", "Team A", "author");
        namespace.setType(com.iflytek.skillhub.domain.namespace.NamespaceType.valueOf(namespaceType));
        var skill = new Skill(7L, "demo", "author", SkillVisibility.NAMESPACE_ONLY);
        setField(skill, "id", 11L);
        if (path.equals("id")) {
            given(skillRepository.findById(11L)).willReturn(Optional.of(skill));
        } else {
            given(skillRepository.findByNamespaceSlugAndSlug("team-a", "demo")).willReturn(List.of(skill));
        }
        if (path.equals("id") || (provider.equals("api_token") && !role.equals("SUPER_ADMIN"))) {
            given(namespaceRepository.findById(7L)).willReturn(Optional.of(namespace));
        }
        if (currentMember) {
            given(namespaceRepository.findLockedById(7L)).willReturn(Optional.of(namespace));
            given(members.findByNamespaceIdAndUserId(7L, "author")).willReturn(Optional.of(
                    new com.iflytek.skillhub.domain.namespace.NamespaceMember(
                            7L, "author", com.iflytek.skillhub.domain.namespace.NamespaceRole.MEMBER)));
        }
        String actor = role.equals("SUPER_ADMIN") ? "platform-admin" : "author";
        var principal = new PlatformPrincipal(actor, actor, null, null, provider, Set.of(role));

        SkillDeleteAppService.DeleteResult result = path.equals("id")
                ? service.deleteSkillByIdFromPortal(11L, principal, null)
                : service.deleteSkillFromPortal("team-a", "demo", null, principal, null);

        assertThat(result.deleted()).isTrue();
        verify(searchIndexService).remove(11L);
        verify(skillHardDeleteService).hardDeleteSkill(skill, "team-a", actor, null, null);
    }

    @Test
    void deleteSkillByIdFromPortal_allowsOwner() {
        Skill skill = new Skill(1L, "demo-skill", "owner-1", SkillVisibility.PUBLIC);
        setField(skill, "id", 11L);
        Namespace namespace = new Namespace("global", "Global", "system");
        setField(namespace, "id", 1L);
        given(skillRepository.findById(11L)).willReturn(java.util.Optional.of(skill));
        given(namespaceRepository.findById(1L)).willReturn(java.util.Optional.of(namespace));

        SkillDeleteAppService.DeleteResult result = service.deleteSkillByIdFromPortal(
                11L,
                new PlatformPrincipal("owner-1", "Owner", "owner@example.com", "", "session", java.util.Set.of("USER")),
                new AuditRequestContext("127.0.0.1", "JUnit")
        );

        assertThat(result.deleted()).isTrue();
        assertThat(result.skillId()).isEqualTo(11L);
        verify(skillHardDeleteService).hardDeleteSkill(skill, "global", "owner-1", "127.0.0.1", "JUnit");
    }

    @Test
    void deleteSkillByIdFromPortal_rejectsNonOwnerWithoutSuperAdmin() {
        Skill skill = new Skill(1L, "demo-skill", "owner-1", SkillVisibility.PUBLIC);
        setField(skill, "id", 11L);
        Namespace namespace = new Namespace("global", "Global", "system");
        setField(namespace, "id", 1L);
        given(skillRepository.findById(11L)).willReturn(java.util.Optional.of(skill));
        given(namespaceRepository.findById(1L)).willReturn(java.util.Optional.of(namespace));

        assertThatThrownBy(() -> service.deleteSkillByIdFromPortal(
                11L,
                new PlatformPrincipal("user-2", "User", "user@example.com", "", "session", java.util.Set.of("USER")),
                new AuditRequestContext("127.0.0.1", "JUnit")
        )).isInstanceOf(DomainForbiddenException.class);
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
