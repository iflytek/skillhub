package com.iflytek.skillhub.domain.skill;

import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkillOwnerAccessPolicyTest {
    private final NamespaceRepository namespaces = mock(NamespaceRepository.class);
    private final NamespaceMemberRepository members = mock(NamespaceMemberRepository.class);
    private final SkillOwnerAccessPolicy policy = new SkillOwnerAccessPolicy(namespaces, members);
    private final Skill skill = new Skill(1L, "owned", "owner", SkillVisibility.PRIVATE);

    @Test
    void removedTeamOwnerIsReadOnlyAndRejoiningRestoresMaintenance() {
        Namespace namespace = mock(Namespace.class);
        when(namespace.getType()).thenReturn(NamespaceType.TEAM);
        when(namespaces.findById(1L)).thenReturn(Optional.of(namespace));
        when(members.findByNamespaceIdAndUserId(1L, "owner")).thenReturn(Optional.empty());
        assertTrue(policy.isReadOnly(skill, "owner", Set.of()));
        assertThrows(DomainForbiddenException.class, () -> policy.assertCanWrite(skill, "owner", Set.of()));
        when(members.findByNamespaceIdAndUserId(1L, "owner")).thenReturn(Optional.of(mock(NamespaceMember.class)));
        assertFalse(policy.isReadOnly(skill, "owner", Set.of()));
        assertDoesNotThrow(() -> policy.assertCanWrite(skill, "owner", Set.of()));
    }

    @Test
    void globalOwnerDoesNotAcquireTeamRestriction() {
        Namespace namespace = mock(Namespace.class);
        when(namespace.getType()).thenReturn(NamespaceType.GLOBAL);
        when(namespaces.findById(1L)).thenReturn(Optional.of(namespace));
        assertFalse(policy.isReadOnly(skill, "owner", Set.of()));
        verifyNoInteractions(members);
    }

    @Test
    void nonOwnerAndSuperAdminRetainTheirIndependentAuthorizationPaths() {
        assertFalse(policy.isReadOnly(skill, "admin", Set.of()));
        assertFalse(policy.isReadOnly(skill, "owner", Set.of("SUPER_ADMIN")));
        verifyNoInteractions(namespaces, members);
    }
}
