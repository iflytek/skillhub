package com.iflytek.skillhub.auth.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.entity.IdentityBinding;
import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.oauth.AccountDisabledException;
import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import com.iflytek.skillhub.auth.oauth.AccountPendingException;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.namespace.GlobalNamespaceMembershipService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class IdentityBindingServiceTest {

    @Mock
    private IdentityBindingRepository bindingRepo;

    @Mock
    private UserAccountRepository userRepo;

    @Mock
    private UserRoleBindingRepository roleBindingRepo;

    @Mock
    private GlobalNamespaceMembershipService globalNamespaceMembershipService;

    private IdentityBindingService service;

    @BeforeEach
    void setUp() {
        service = new IdentityBindingService(bindingRepo, userRepo, roleBindingRepo, globalNamespaceMembershipService);
    }

    @Test
    void bindOrCreate_assignsGlobalMembershipForActiveNewUsers() {
        OAuthClaims claims = new OAuthClaims(
                "github",
                "gh_1",
                "alice@example.com",
                true,
                "alice",
                Map.of("avatar_url", "https://example.test/a.png")
        );
        when(bindingRepo.findByProviderCodeAndSubject("github", "gh_1")).thenReturn(Optional.empty());
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleBindingRepo.findByUserId(any())).thenReturn(List.of());

        PlatformPrincipal principal = service.bindOrCreate(claims, UserStatus.ACTIVE);

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userRepo).save(userCaptor.capture());
        verify(globalNamespaceMembershipService).ensureMember(userCaptor.getValue().getId());
        verify(bindingRepo).save(any(IdentityBinding.class));
        assertThat(principal.displayName()).isEqualTo("alice");
        assertThat(principal.oauthProvider()).isEqualTo("github");
    }

    @Test
    void bindOrCreate_doesNotAssignGlobalMembershipForPendingUsers() {
        OAuthClaims claims = new OAuthClaims(
                "github",
                "gh_1",
                "alice@example.com",
                true,
                "alice",
                Map.of()
        );
        when(bindingRepo.findByProviderCodeAndSubject("github", "gh_1")).thenReturn(Optional.empty());
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.bindOrCreate(claims, UserStatus.PENDING))
                .isInstanceOf(AccountPendingException.class);

        verify(globalNamespaceMembershipService, never()).ensureMember(any());
    }

    @Test
    void bindOrCreate_defaultsToUserRoleWhenNoBindingsExist() {
        OAuthClaims claims = new OAuthClaims(
                "github",
                "gh_1",
                "alice@example.com",
                true,
                "alice",
                Map.of()
        );
        when(bindingRepo.findByProviderCodeAndSubject("github", "gh_1")).thenReturn(Optional.empty());
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleBindingRepo.findByUserId(any())).thenReturn(List.of());

        PlatformPrincipal principal = service.bindOrCreate(claims, UserStatus.ACTIVE);

        assertThat(principal.platformRoles()).containsExactly("USER");
    }

    @Test
    void bindOrCreate_existingDisabledUser_throwsAccountDisabled() {
        OAuthClaims claims = new OAuthClaims(
                "github",
                "gh_1",
                "alice@example.com",
                true,
                "alice",
                Map.of()
        );
        IdentityBinding binding = new IdentityBinding("usr_1", "github", "gh_1", "alice");
        UserAccount user = new UserAccount("usr_1", "alice", "alice@example.com", null);
        user.setStatus(UserStatus.DISABLED);

        when(bindingRepo.findByProviderCodeAndSubject("github", "gh_1")).thenReturn(Optional.of(binding));
        when(userRepo.findById("usr_1")).thenReturn(Optional.of(user));
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.bindOrCreate(claims, UserStatus.ACTIVE))
                .isInstanceOf(AccountDisabledException.class);
    }

    @Test
    void bindOrCreate_returnsExplicitPlatformRolesWhenBindingsExist() {
        OAuthClaims claims = new OAuthClaims(
                "github",
                "gh_1",
                "alice@example.com",
                true,
                "alice",
                Map.of()
        );
        when(bindingRepo.findByProviderCodeAndSubject("github", "gh_1")).thenReturn(Optional.empty());
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Role role = new Role();
        ReflectionTestUtils.setField(role, "code", "AUDITOR");
        when(roleBindingRepo.findByUserId(any())).thenReturn(List.of(new UserRoleBinding("usr_1", role)));

        PlatformPrincipal principal = service.bindOrCreate(claims, UserStatus.ACTIVE);

        assertThat(principal.platformRoles()).containsExactly("AUDITOR");
    }

    @Test
    void createPendingUserIfAbsent_existingDisabledBinding_throwsAccountDisabled() {
        OAuthClaims claims = new OAuthClaims(
                "github",
                "gh_1",
                "alice@example.com",
                true,
                "alice",
                Map.of()
        );
        IdentityBinding binding = new IdentityBinding("usr_1", "github", "gh_1", "alice");
        UserAccount user = new UserAccount("usr_1", "alice", "alice@example.com", null);
        user.setStatus(UserStatus.DISABLED);

        when(bindingRepo.findByProviderCodeAndSubject("github", "gh_1")).thenReturn(Optional.of(binding));
        when(userRepo.findById("usr_1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.createPendingUserIfAbsent(claims))
                .isInstanceOf(AccountDisabledException.class);
    }

    @Test
    void bindOrCreate_existingPendingUser_throwsAccountPending() {
        OAuthClaims claims = new OAuthClaims(
                "github",
                "gh_1",
                "alice@example.com",
                true,
                "alice",
                Map.of()
        );
        IdentityBinding binding = new IdentityBinding("usr_1", "github", "gh_1", "alice");
        UserAccount user = new UserAccount("usr_1", "alice", "alice@example.com", null);
        user.setStatus(UserStatus.PENDING);

        when(bindingRepo.findByProviderCodeAndSubject("github", "gh_1")).thenReturn(Optional.of(binding));
        when(userRepo.findById("usr_1")).thenReturn(Optional.of(user));
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.bindOrCreate(claims, UserStatus.ACTIVE))
                .isInstanceOf(AccountPendingException.class);
    }

    @Test
    void createPendingUserIfAbsent_existingPendingBinding_throwsAccountPending() {
        OAuthClaims claims = new OAuthClaims(
                "github",
                "gh_1",
                "alice@example.com",
                true,
                "alice",
                Map.of()
        );
        IdentityBinding binding = new IdentityBinding("usr_1", "github", "gh_1", "alice");
        UserAccount user = new UserAccount("usr_1", "alice", "alice@example.com", null);
        user.setStatus(UserStatus.PENDING);

        when(bindingRepo.findByProviderCodeAndSubject("github", "gh_1")).thenReturn(Optional.of(binding));
        when(userRepo.findById("usr_1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.createPendingUserIfAbsent(claims))
                .isInstanceOf(AccountPendingException.class);
    }

    @Test
    void bindOrCreate_resolvesExistingUserByUssId() {
        OAuthClaims claims = new OAuthClaims(
                "uass",
                "uss_1",
                "alice@example.com",
                true,
                "alice",
                Map.of("uss_id", "uss-123", "avatar_url", "https://example.test/a.png")
        );
        UserAccount existingUser = new UserAccount("usr_existing", "old-name", "old@example.com", null);
        existingUser.setUssId("uss-123");

        when(bindingRepo.findByProviderCodeAndSubject("uass", "uss_1")).thenReturn(Optional.empty());
        when(userRepo.findByUssId("uss-123")).thenReturn(Optional.of(existingUser));
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleBindingRepo.findByUserId(any())).thenReturn(List.of());

        PlatformPrincipal principal = service.bindOrCreate(claims, UserStatus.ACTIVE);

        assertThat(principal.displayName()).isEqualTo("alice");
        verify(bindingRepo).save(any(IdentityBinding.class));
    }

    @Test
    void bindOrCreate_refreshUserSkipsNullEmailAndAvatar() {
        OAuthClaims claims = new OAuthClaims(
                "github",
                "gh_1",
                null,
                true,
                "alice",
                Map.of()
        );
        when(bindingRepo.findByProviderCodeAndSubject("github", "gh_1")).thenReturn(Optional.empty());
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleBindingRepo.findByUserId(any())).thenReturn(List.of());

        PlatformPrincipal principal = service.bindOrCreate(claims, UserStatus.ACTIVE);

        assertThat(principal.displayName()).isEqualTo("alice");
    }

    @Test
    void bindOrCreateResult_returnsNewlyCreatedFlag() {
        OAuthClaims claims = new OAuthClaims(
                "github",
                "gh_1",
                "alice@example.com",
                true,
                "alice",
                Map.of()
        );
        when(bindingRepo.findByProviderCodeAndSubject("github", "gh_1")).thenReturn(Optional.empty());
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleBindingRepo.findByUserId(any())).thenReturn(List.of());

        IdentityBindingService.BindOrCreateResult result = service.bindOrCreateResult(claims, UserStatus.ACTIVE);

        assertThat(result.newlyCreated()).isTrue();
        assertThat(result.principal()).isNotNull();
    }

    @Test
    void createPendingUserIfAbsent_noExistingBinding_createsPendingUserAndBinding() {
        OAuthClaims claims = new OAuthClaims(
                "github",
                "gh_1",
                "alice@example.com",
                true,
                "alice",
                Map.of()
        );
        when(bindingRepo.findByProviderCodeAndSubject("github", "gh_1")).thenReturn(Optional.empty());
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createPendingUserIfAbsent(claims);

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userRepo).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getStatus()).isEqualTo(UserStatus.PENDING);
        verify(bindingRepo).save(any(IdentityBinding.class));
    }

    @Test
    void bindOrCreate_existingBinding_refreshesAvatarAndUssId() {
        OAuthClaims claims = new OAuthClaims(
                "github",
                "gh_1",
                "new@example.com",
                true,
                "newname",
                Map.of("avatar_url", "https://new.test/a.png", "uss_id", "uss-999")
        );
        IdentityBinding binding = new IdentityBinding("usr_1", "github", "gh_1", "alice");
        UserAccount user = new UserAccount("usr_1", "alice", "old@example.com", "https://old.test/a.png");
        user.setUssId("uss-old");
        user.setStatus(UserStatus.ACTIVE);

        when(bindingRepo.findByProviderCodeAndSubject("github", "gh_1")).thenReturn(Optional.of(binding));
        when(userRepo.findById("usr_1")).thenReturn(Optional.of(user));
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleBindingRepo.findByUserId("usr_1")).thenReturn(List.of());

        PlatformPrincipal principal = service.bindOrCreate(claims, UserStatus.ACTIVE);

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userRepo).save(userCaptor.capture());
        UserAccount saved = userCaptor.getValue();
        assertThat(saved.getDisplayName()).isEqualTo("newname");
        assertThat(saved.getEmail()).isEqualTo("new@example.com");
        assertThat(saved.getAvatarUrl()).isEqualTo("https://new.test/a.png");
        assertThat(saved.getUssId()).isEqualTo("uss-999");
        assertThat(principal.displayName()).isEqualTo("newname");
    }

    @Test
    void bindOrCreate_existingUserResolvedByUssId_doesNotCallEnsureMember() {
        OAuthClaims claims = new OAuthClaims(
                "uass",
                "uss_1",
                "alice@example.com",
                true,
                "alice",
                Map.of("uss_id", "uss-123")
        );
        UserAccount existingUser = new UserAccount("usr_existing", "old-name", "old@example.com", null);
        existingUser.setUssId("uss-123");
        existingUser.setStatus(UserStatus.ACTIVE);

        when(bindingRepo.findByProviderCodeAndSubject("uass", "uss_1")).thenReturn(Optional.empty());
        when(userRepo.findByUssId("uss-123")).thenReturn(Optional.of(existingUser));
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleBindingRepo.findByUserId(any())).thenReturn(List.of());

        IdentityBindingService.BindOrCreateResult result = service.bindOrCreateResult(claims, UserStatus.ACTIVE);

        assertThat(result.newlyCreated()).isFalse();
        verify(globalNamespaceMembershipService, never()).ensureMember(any());
    }

    @Test
    void bindOrCreate_resolveExistingUser_nonUassProvider_returnsEmpty() {
        OAuthClaims claims = new OAuthClaims(
                "github",
                "gh_1",
                "alice@example.com",
                true,
                "alice",
                Map.of("uss_id", "uss-123")
        );
        when(bindingRepo.findByProviderCodeAndSubject("github", "gh_1")).thenReturn(Optional.empty());
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleBindingRepo.findByUserId(any())).thenReturn(List.of());

        PlatformPrincipal principal = service.bindOrCreate(claims, UserStatus.ACTIVE);

        verify(userRepo, never()).findByUssId(any());
        assertThat(principal.displayName()).isEqualTo("alice");
    }

    @Test
    void bindOrCreate_resolveExistingUser_uassWithBlankUssId_returnsEmpty() {
        OAuthClaims claims = new OAuthClaims(
                "uass",
                "uss_1",
                "alice@example.com",
                true,
                "alice",
                Map.of("uss_id", "  ")
        );
        when(bindingRepo.findByProviderCodeAndSubject("uass", "uss_1")).thenReturn(Optional.empty());
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleBindingRepo.findByUserId(any())).thenReturn(List.of());

        PlatformPrincipal principal = service.bindOrCreate(claims, UserStatus.ACTIVE);

        verify(userRepo, never()).findByUssId(any());
        assertThat(principal.displayName()).isEqualTo("alice");
    }

    @Test
    void bindOrCreate_newUserFromClaims_skipsNullAvatarAndUssId() {
        OAuthClaims claims = new OAuthClaims(
                "github",
                "gh_1",
                "alice@example.com",
                true,
                "alice",
                Map.of()
        );
        when(bindingRepo.findByProviderCodeAndSubject("github", "gh_1")).thenReturn(Optional.empty());
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleBindingRepo.findByUserId(any())).thenReturn(List.of());

        PlatformPrincipal principal = service.bindOrCreate(claims, UserStatus.ACTIVE);

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userRepo).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getAvatarUrl()).isNull();
        assertThat(userCaptor.getValue().getUssId()).isNull();
    }

    @Test
    void bindOrCreate_newUserFromClaims_nonStringExtraValue_returnsNull() {
        OAuthClaims claims = new OAuthClaims(
                "github",
                "gh_1",
                "alice@example.com",
                true,
                "alice",
                Map.of("avatar_url", 123, "uss_id", List.of("x"))
        );
        when(bindingRepo.findByProviderCodeAndSubject("github", "gh_1")).thenReturn(Optional.empty());
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleBindingRepo.findByUserId(any())).thenReturn(List.of());

        PlatformPrincipal principal = service.bindOrCreate(claims, UserStatus.ACTIVE);

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userRepo).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getAvatarUrl()).isNull();
        assertThat(userCaptor.getValue().getUssId()).isNull();
    }

    @Test
    void bindOrCreate_newUserFromClaims_blankStringExtraValue_returnsNull() {
        OAuthClaims claims = new OAuthClaims(
                "github",
                "gh_1",
                "alice@example.com",
                true,
                "alice",
                Map.of("avatar_url", "   ", "uss_id", "")
        );
        when(bindingRepo.findByProviderCodeAndSubject("github", "gh_1")).thenReturn(Optional.empty());
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleBindingRepo.findByUserId(any())).thenReturn(List.of());

        PlatformPrincipal principal = service.bindOrCreate(claims, UserStatus.ACTIVE);

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userRepo).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getAvatarUrl()).isNull();
        assertThat(userCaptor.getValue().getUssId()).isNull();
    }
}
