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
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentJpaRepository;
import com.iflytek.skillhub.search.SearchIndexService;
import com.iflytek.skillhub.search.SkillSearchDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    @Mock private LocalCredentialRepository localCredentialRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRoleBindingRepository userRoleBindingRepository;
    @Mock private SkillRepository skillRepository;
    @Mock private SkillVersionRepository skillVersionRepository;
    @Mock private SkillSearchDocumentJpaRepository skillSearchDocumentJpaRepository;
    @Mock private SearchIndexService searchIndexService;
    @Mock private PasswordEncoder passwordEncoder;

    private LocalDevDataInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new LocalDevDataInitializer(
                userAccountRepository,
                namespaceRepository,
                namespaceMemberRepository,
                localCredentialRepository,
                roleRepository,
                userRoleBindingRepository,
                skillRepository,
                skillVersionRepository,
                skillSearchDocumentJpaRepository,
                searchIndexService,
                passwordEncoder
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
        when(namespaceRepository.save(any(Namespace.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(anyLong(), any())).thenReturn(Optional.empty());
        when(namespaceMemberRepository.save(any(NamespaceMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleRepository.findByCode(anyString())).thenAnswer(invocation -> {
            String code = invocation.getArgument(0, String.class);
            Role role = new Role();
            setField(role, "id", 1L);
            setField(role, "code", code);
            return Optional.of(role);
        });
        when(userRoleBindingRepository.findByUserId(LocalDevDataInitializer.LOCAL_ADMIN_ID)).thenReturn(List.of());
        when(localCredentialRepository.findByUserId(anyString())).thenReturn(Optional.empty());
        when(localCredentialRepository.save(any(LocalCredential.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(skillRepository.findByNamespaceIdAndSlug(anyLong(), anyString())).thenReturn(List.of());
        when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> {
            Skill skill = invocation.getArgument(0);
            if (skill.getId() == null) {
                setField(skill, "id", 101L);
            }
            return skill;
        });
        when(skillVersionRepository.findBySkillIdAndVersion(anyLong(), anyString())).thenReturn(Optional.empty());
        when(skillVersionRepository.save(any(SkillVersion.class))).thenAnswer(invocation -> {
            SkillVersion version = invocation.getArgument(0);
            if (version.getId() == null) {
                setField(version, "id", 201L);
            }
            return version;
        });
        when(skillSearchDocumentJpaRepository.findBySkillId(anyLong())).thenReturn(Optional.empty());
        when(skillSearchDocumentJpaRepository.save(any(SkillSearchDocumentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");

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
        verify(skillRepository, atLeastOnce()).save(any(Skill.class));
        verify(skillVersionRepository).save(any(SkillVersion.class));
        verify(skillSearchDocumentJpaRepository).save(any(SkillSearchDocumentEntity.class));
        verify(searchIndexService).index(any(SkillSearchDocument.class));
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
