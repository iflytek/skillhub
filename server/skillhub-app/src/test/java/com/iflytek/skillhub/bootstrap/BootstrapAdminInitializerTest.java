package com.iflytek.skillhub.bootstrap;

import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.local.LocalCredential;
import com.iflytek.skillhub.auth.local.LocalCredentialRepository;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BootstrapAdminInitializerTest {

    @Mock private UserAccountRepository userAccountRepository;
    @Mock private LocalCredentialRepository localCredentialRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRoleBindingRepository userRoleBindingRepository;
    @Mock private NamespaceRepository namespaceRepository;
    @Mock private NamespaceMemberRepository namespaceMemberRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private BootstrapAdminProperties bootstrapAdminProperties;
    private BootstrapAdminInitializer initializer;

    @BeforeEach
    void setUp() {
        bootstrapAdminProperties = new BootstrapAdminProperties();
        initializer = new BootstrapAdminInitializer(
                bootstrapAdminProperties,
                userAccountRepository,
                localCredentialRepository,
                roleRepository,
                userRoleBindingRepository,
                namespaceRepository,
                namespaceMemberRepository,
                passwordEncoder
        );
    }

    @Test
    void shouldSeedBootstrapAdminWithCredentialRoleAndMembership() throws Exception {
        bootstrapAdminProperties.setEnabled(true);
        Namespace global = new Namespace("global", "Global", "system");
        setField(global, "id", 1L);

        Role superAdminRole = new Role();
        setField(superAdminRole, "id", 1L);
        setField(superAdminRole, "code", "SUPER_ADMIN");

        when(localCredentialRepository.existsByUsernameIgnoreCase("admin")).thenReturn(false);
        when(userAccountRepository.findById("docker-admin")).thenReturn(Optional.empty());
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode("ChangeMe!2026")).thenReturn("encoded-password");
        when(roleRepository.findByCode("SUPER_ADMIN")).thenReturn(Optional.of(superAdminRole));
        when(userRoleBindingRepository.findByUserId("docker-admin")).thenReturn(List.of());
        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(global));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "docker-admin")).thenReturn(Optional.empty());

        initializer.run(new DefaultApplicationArguments(new String[0]));

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository, atLeastOnce()).save(userCaptor.capture());
        UserAccount savedUser = userCaptor.getAllValues().getLast();
        assertEquals("docker-admin", savedUser.getId());
        assertEquals("Admin", savedUser.getDisplayName());
        assertEquals("admin@skillhub.local", savedUser.getEmail());

        ArgumentCaptor<LocalCredential> credentialCaptor = ArgumentCaptor.forClass(LocalCredential.class);
        verify(localCredentialRepository).save(credentialCaptor.capture());
        assertEquals("docker-admin", credentialCaptor.getValue().getUserId());
        assertEquals("admin", credentialCaptor.getValue().getUsername());
        assertEquals("encoded-password", credentialCaptor.getValue().getPasswordHash());

        ArgumentCaptor<UserRoleBinding> roleBindingCaptor = ArgumentCaptor.forClass(UserRoleBinding.class);
        verify(userRoleBindingRepository).save(roleBindingCaptor.capture());
        assertEquals("docker-admin", roleBindingCaptor.getValue().getUserId());
        assertEquals("SUPER_ADMIN", roleBindingCaptor.getValue().getRole().getCode());

        ArgumentCaptor<NamespaceMember> memberCaptor = ArgumentCaptor.forClass(NamespaceMember.class);
        verify(namespaceMemberRepository).save(memberCaptor.capture());
        assertEquals("docker-admin", memberCaptor.getValue().getUserId());
        assertEquals(NamespaceRole.OWNER, memberCaptor.getValue().getRole());
    }

    @Test
    void shouldSkipWhenBootstrapAdminCredentialAlreadyExists() {
        bootstrapAdminProperties.setEnabled(true);
        when(localCredentialRepository.existsByUsernameIgnoreCase("admin")).thenReturn(true);

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(userAccountRepository, never()).save(any(UserAccount.class));
        verify(localCredentialRepository, never()).save(any(LocalCredential.class));
        verify(userRoleBindingRepository, never()).save(any(UserRoleBinding.class));
        verify(namespaceMemberRepository, never()).save(any(NamespaceMember.class));
    }

    @Test
    void shouldSkipWhenBootstrapAdminIsDisabled() {
        bootstrapAdminProperties.setEnabled(false);

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(localCredentialRepository, never()).existsByUsernameIgnoreCase(any());
        verify(userAccountRepository, never()).save(any(UserAccount.class));
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
