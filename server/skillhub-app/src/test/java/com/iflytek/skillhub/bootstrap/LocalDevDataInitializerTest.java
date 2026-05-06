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
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.user.UserStatus;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentJpaRepository;
import com.iflytek.skillhub.search.SearchIndexService;
import com.iflytek.skillhub.search.SkillSearchDocument;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    void shouldUpdateExistingSeedDataAndReuseExistingSearchFixture() throws Exception {
        UserAccount existingLocalUser = new UserAccount(LocalDevDataInitializer.LOCAL_USER_ID, "Old User", "old-user@example.test", null);
        existingLocalUser.setStatus(UserStatus.DISABLED);
        UserAccount existingLocalAdmin = new UserAccount(LocalDevDataInitializer.LOCAL_ADMIN_ID, "Old Admin", "old-admin@example.test", null);
        existingLocalAdmin.setStatus(UserStatus.DISABLED);

        NamespaceMember existingMember = new NamespaceMember(55L, LocalDevDataInitializer.LOCAL_USER_ID, NamespaceRole.MEMBER);

        LocalCredential localUserCredential = new LocalCredential(LocalDevDataInitializer.LOCAL_USER_ID, "local-user", "old-hash");
        LocalCredential localAdminCredential = new LocalCredential(LocalDevDataInitializer.LOCAL_ADMIN_ID, "admin", "old-admin-hash");

        Skill existingSkill = new Skill(55L, LocalDevDataInitializer.LOCAL_SEARCH_FIXTURE_SLUG, LocalDevDataInitializer.LOCAL_ADMIN_ID, SkillVisibility.PRIVATE);
        setField(existingSkill, "id", 101L);
        existingSkill.setDisplayName("Old Fixture");
        existingSkill.setSummary("Old summary");

        Instant publishedAt = Instant.parse("2026-05-04T01:02:03Z");
        SkillVersion existingVersion = new SkillVersion(101L, "1.0.0", LocalDevDataInitializer.LOCAL_ADMIN_ID);
        setField(existingVersion, "id", 201L);
        existingVersion.setPublishedAt(publishedAt);

        SkillSearchDocumentEntity existingDocument = new SkillSearchDocumentEntity(
                101L,
                44L,
                "old-global",
                "old-owner",
                "Old title",
                "Old summary",
                "old,keywords",
                "old search text",
                "old-vector",
                "PRIVATE",
                "INACTIVE"
        );

        Map<String, Role> rolesByCode = new HashMap<>();
        when(roleRepository.findByCode(anyString())).thenAnswer(invocation -> Optional.ofNullable(rolesByCode.get(invocation.getArgument(0, String.class))));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            Role savedRole = invocation.getArgument(0);
            rolesByCode.put(savedRole.getCode(), savedRole);
            return savedRole;
        });
        when(userAccountRepository.findById(LocalDevDataInitializer.LOCAL_USER_ID)).thenReturn(Optional.of(existingLocalUser));
        when(userAccountRepository.findById(LocalDevDataInitializer.LOCAL_ADMIN_ID)).thenReturn(Optional.of(existingLocalAdmin));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.empty());
        when(namespaceRepository.save(any(Namespace.class))).thenAnswer(invocation -> {
            Namespace savedNamespace = invocation.getArgument(0);
            if (savedNamespace.getId() == null) {
                setField(savedNamespace, "id", 55L);
            }
            return savedNamespace;
        });
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(55L, LocalDevDataInitializer.LOCAL_USER_ID))
                .thenReturn(Optional.of(existingMember));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(55L, LocalDevDataInitializer.LOCAL_ADMIN_ID))
                .thenReturn(Optional.empty());
        when(namespaceMemberRepository.save(any(NamespaceMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(localCredentialRepository.findByUserId(LocalDevDataInitializer.LOCAL_USER_ID)).thenReturn(Optional.of(localUserCredential));
        when(localCredentialRepository.findByUserId(LocalDevDataInitializer.LOCAL_ADMIN_ID)).thenReturn(Optional.of(localAdminCredential));
        when(localCredentialRepository.save(any(LocalCredential.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Role superAdminRole = new Role();
        setField(superAdminRole, "code", "SUPER_ADMIN");
        when(userRoleBindingRepository.findByUserId(LocalDevDataInitializer.LOCAL_ADMIN_ID))
                .thenReturn(List.of(new UserRoleBinding(LocalDevDataInitializer.LOCAL_ADMIN_ID, superAdminRole)));

        when(skillRepository.findByNamespaceIdAndSlug(55L, LocalDevDataInitializer.LOCAL_SEARCH_FIXTURE_SLUG))
                .thenReturn(List.of(existingSkill));
        when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(skillVersionRepository.findBySkillIdAndVersion(101L, "1.0.0")).thenReturn(Optional.of(existingVersion));
        when(skillVersionRepository.save(any(SkillVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(skillSearchDocumentJpaRepository.findBySkillId(101L)).thenReturn(Optional.of(existingDocument));
        when(skillSearchDocumentJpaRepository.save(any(SkillSearchDocumentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "encoded-" + invocation.getArgument(0, String.class));

        initializer.run(new DefaultApplicationArguments(new String[0]));

        assertEquals("Local Developer", existingLocalUser.getDisplayName());
        assertEquals("local-user@example.test", existingLocalUser.getEmail());
        assertEquals(UserStatus.ACTIVE, existingLocalUser.getStatus());
        assertEquals("Local Admin", existingLocalAdmin.getDisplayName());
        assertEquals("local-admin@example.test", existingLocalAdmin.getEmail());
        assertEquals(UserStatus.ACTIVE, existingLocalAdmin.getStatus());

        assertEquals(NamespaceRole.OWNER, existingMember.getRole());
        assertEquals(4, rolesByCode.size());
        Role savedAuditorRole = rolesByCode.get("AUDITOR");
        assertNotNull(savedAuditorRole);
        assertEquals("审计员", savedAuditorRole.getName());
        assertTrue(savedAuditorRole.isSystem());

        assertEquals("encoded-ChangeMe!2026", localUserCredential.getPasswordHash());
        assertEquals("encoded-ChangeMe!2026", localAdminCredential.getPasswordHash());

        assertEquals("Dev Search Fixture", existingSkill.getDisplayName());
        assertEquals("Seeded searchable skill for dev runtime verification.", existingSkill.getSummary());
        assertEquals(LocalDevDataInitializer.LOCAL_ADMIN_ID, existingSkill.getCreatedBy());
        assertEquals(LocalDevDataInitializer.LOCAL_ADMIN_ID, existingSkill.getUpdatedBy());
        assertEquals(SkillVisibility.PUBLIC, existingSkill.getVisibility());
        assertEquals(201L, existingSkill.getLatestVersionId());

        assertEquals(publishedAt, existingVersion.getPublishedAt());
        assertTrue(existingVersion.isBundleReady());
        assertTrue(existingVersion.isDownloadReady());

        assertEquals(55L, existingDocument.getNamespaceId());
        assertEquals("global", existingDocument.getNamespaceSlug());
        assertEquals(LocalDevDataInitializer.LOCAL_ADMIN_ID, existingDocument.getOwnerId());
        assertEquals("PUBLIC", existingDocument.getVisibility());
        assertEquals("ACTIVE", existingDocument.getStatus());
        assertEquals("mysql-runtime-fixture mysql runtime fixture searchable local dev seeded skill", existingDocument.getSearchText());
        assertEquals("", existingDocument.getSemanticVector());

        ArgumentCaptor<SkillSearchDocument> documentCaptor = ArgumentCaptor.forClass(SkillSearchDocument.class);
        verify(searchIndexService).index(documentCaptor.capture());
        SkillSearchDocument indexedDocument = documentCaptor.getValue();
        assertEquals(101L, indexedDocument.skillId());
        assertEquals(55L, indexedDocument.namespaceId());
        assertEquals("global", indexedDocument.namespaceSlug());
        assertEquals(publishedAt.toEpochMilli(), indexedDocument.updatedAtEpochMillis());
        assertEquals("ACTIVE", indexedDocument.namespaceStatus());
        assertFalse(indexedDocument.hidden());

        ArgumentCaptor<Namespace> namespaceCaptor = ArgumentCaptor.forClass(Namespace.class);
        verify(namespaceRepository).save(namespaceCaptor.capture());
        Namespace createdGlobal = namespaceCaptor.getValue();
        assertEquals("global", createdGlobal.getSlug());
        assertEquals("Global", createdGlobal.getDisplayName());
        assertEquals("Platform-level public namespace", createdGlobal.getDescription());
        assertEquals(NamespaceType.GLOBAL, createdGlobal.getType());
        assertEquals(com.iflytek.skillhub.domain.namespace.NamespaceStatus.ACTIVE, createdGlobal.getStatus());

        verify(userRoleBindingRepository, never()).save(any(UserRoleBinding.class));
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
