package com.iflytek.skillhub.bootstrap;

import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.repository.RoleRepository;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.DefaultApplicationArguments;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocalDevDataInitializerTest {

    @Mock private UserAccountRepository userAccountRepository;
    @Mock private NamespaceRepository namespaceRepository;
    @Mock private NamespaceMemberRepository namespaceMemberRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRoleBindingRepository userRoleBindingRepository;

    private LocalDevDataInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new LocalDevDataInitializer(
                userAccountRepository,
                namespaceRepository,
                namespaceMemberRepository,
                roleRepository,
                userRoleBindingRepository
        );
    }

    @Test
    void shouldSeedLocalUsersGlobalMembershipAndSuperAdminRole() throws Exception {
        Namespace global = new Namespace("global", "Global", "system");
        setField(global, "id", 1L);

        Role superAdminRole = new Role();
        setField(superAdminRole, "id", 1L);
        setField(superAdminRole, "code", "SUPER_ADMIN");

        when(userAccountRepository.findById(LocalDevDataInitializer.LOCAL_USER_ID)).thenReturn(Optional.empty());
        when(userAccountRepository.findById(LocalDevDataInitializer.LOCAL_ADMIN_ID)).thenReturn(Optional.empty());
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(global));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(anyLong(), any())).thenReturn(Optional.empty());
        when(namespaceMemberRepository.save(any(NamespaceMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleRepository.findByCode("SUPER_ADMIN")).thenReturn(Optional.of(superAdminRole));
        when(userRoleBindingRepository.findByUserId(LocalDevDataInitializer.LOCAL_ADMIN_ID)).thenReturn(List.of());

        initializer.run(new DefaultApplicationArguments(new String[0]));

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository, times(2)).save(userCaptor.capture());
        List<UserAccount> savedUsers = userCaptor.getAllValues();
        assertTrue(savedUsers.stream().anyMatch(user -> LocalDevDataInitializer.LOCAL_USER_ID.equals(user.getId())));
        assertTrue(savedUsers.stream().anyMatch(user -> LocalDevDataInitializer.LOCAL_ADMIN_ID.equals(user.getId())));

        ArgumentCaptor<NamespaceMember> memberCaptor = ArgumentCaptor.forClass(NamespaceMember.class);
        verify(namespaceMemberRepository, times(2)).save(memberCaptor.capture());
        assertEquals(
                List.of(LocalDevDataInitializer.LOCAL_USER_ID, LocalDevDataInitializer.LOCAL_ADMIN_ID),
                memberCaptor.getAllValues().stream().map(NamespaceMember::getUserId).toList()
        );
        assertTrue(memberCaptor.getAllValues().stream().allMatch(member -> member.getRole() == NamespaceRole.OWNER));

        ArgumentCaptor<UserRoleBinding> roleBindingCaptor = ArgumentCaptor.forClass(UserRoleBinding.class);
        verify(userRoleBindingRepository).save(roleBindingCaptor.capture());
        assertEquals(LocalDevDataInitializer.LOCAL_ADMIN_ID, roleBindingCaptor.getValue().getUserId());
        assertEquals("SUPER_ADMIN", roleBindingCaptor.getValue().getRole().getCode());
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
