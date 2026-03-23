package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipientResolverTest {

    @Mock
    NamespaceMemberRepository namespaceMemberRepository;

    @Mock
    UserRoleBindingRepository userRoleBindingRepository;

    @InjectMocks
    RecipientResolver resolver;

    @Test
    void resolveNamespaceAdmins_shouldReturnAdminAndOwnerUserIds() {
        NamespaceMember owner = new NamespaceMember(1L, "user-owner", NamespaceRole.OWNER);
        NamespaceMember admin = new NamespaceMember(1L, "user-admin", NamespaceRole.ADMIN);
        when(namespaceMemberRepository.findByNamespaceIdAndRoleIn(1L, Set.of(NamespaceRole.OWNER, NamespaceRole.ADMIN)))
                .thenReturn(List.of(owner, admin));

        List<String> result = resolver.resolveNamespaceAdmins(1L);

        assertThat(result).containsExactlyInAnyOrder("user-owner", "user-admin");
    }

    @Test
    void resolveNamespaceAdmins_shouldReturnEmptyWhenNoAdmins() {
        when(namespaceMemberRepository.findByNamespaceIdAndRoleIn(anyLong(), anySet()))
                .thenReturn(List.of());

        List<String> result = resolver.resolveNamespaceAdmins(99L);

        assertThat(result).isEmpty();
    }

    @Test
    void resolvePlatformSkillAdmins_shouldReturnSkillAdminUserIds() {
        UserRoleBinding b1 = mock(UserRoleBinding.class);
        UserRoleBinding b2 = mock(UserRoleBinding.class);
        when(b1.getUserId()).thenReturn("admin-1");
        when(b2.getUserId()).thenReturn("admin-2");
        when(userRoleBindingRepository.findByRole_CodeIn(Set.of("SKILL_ADMIN", "SUPER_ADMIN")))
                .thenReturn(List.of(b1, b2));

        List<String> result = resolver.resolvePlatformSkillAdmins();

        assertThat(result).containsExactlyInAnyOrder("admin-1", "admin-2");
    }

    @Test
    void resolvePlatformSkillAdmins_shouldIncludeSuperAdminsAndDeduplicateUsers() {
        UserRoleBinding skillAdmin = mock(UserRoleBinding.class);
        UserRoleBinding superAdmin = mock(UserRoleBinding.class);
        UserRoleBinding duplicate = mock(UserRoleBinding.class);
        when(skillAdmin.getUserId()).thenReturn("skill-admin");
        when(superAdmin.getUserId()).thenReturn("super-admin");
        when(duplicate.getUserId()).thenReturn("skill-admin");
        when(userRoleBindingRepository.findByRole_CodeIn(Set.of("SKILL_ADMIN", "SUPER_ADMIN")))
                .thenReturn(List.of(skillAdmin, superAdmin, duplicate));

        List<String> result = resolver.resolvePlatformSkillAdmins();

        assertThat(result).containsExactly("skill-admin", "super-admin");
    }
}
