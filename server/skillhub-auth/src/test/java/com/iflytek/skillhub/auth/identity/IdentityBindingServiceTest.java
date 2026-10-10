package com.iflytek.skillhub.auth.identity;

import com.iflytek.skillhub.auth.settings.InitialExternalRoleGrantService;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;

import com.iflytek.skillhub.auth.entity.IdentityBinding;
import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.oauth.AccountDisabledException;
import com.iflytek.skillhub.auth.oauth.AccountMergedException;
import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import com.iflytek.skillhub.auth.oauth.AccountPendingException;
import com.iflytek.skillhub.auth.oauth.SystemAccountLoginException;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.event.UserActivatedEvent;
import com.iflytek.skillhub.domain.namespace.GlobalNamespaceMembershipService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
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

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private InitialExternalRoleGrantService initialRoleGrants;

    private IdentityBindingService service;

    @BeforeEach
    void setUp() {
        service = new IdentityBindingService(bindingRepo, userRepo, roleBindingRepo,
                globalNamespaceMembershipService, eventPublisher, initialRoleGrants,
                new ImmediateTransactionOperations());
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
        verify(bindingRepo).saveAndFlush(any(IdentityBinding.class));
        verify(initialRoleGrants).grantForNewUser(claims, userCaptor.getValue().getId());
        assertThat(principal.displayName()).isEqualTo("alice");
        assertThat(principal.oauthProvider()).isEqualTo("github");
    }

    @Test
    void bindOrCreate_publishesActivationForActiveNewUsers() {
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

        service.bindOrCreate(claims, UserStatus.ACTIVE);

        ArgumentCaptor<UserActivatedEvent> eventCaptor = ArgumentCaptor.forClass(UserActivatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().username()).isEqualTo("alice");
        assertThat(eventCaptor.getValue().email()).isEqualTo("alice@example.com");
    }

    @Test
    void bindOrCreate_doesNotPublishActivationForReturningUsers() {
        OAuthClaims claims = new OAuthClaims("github", "gh_1", "alice@example.com", true, "alice", Map.of());
        UserAccount existing = new UserAccount("usr_1", "alice", "alice@example.com", null);
        existing.setStatus(UserStatus.ACTIVE);
        when(bindingRepo.findByProviderCodeAndSubject("github", "gh_1"))
                .thenReturn(Optional.of(new IdentityBinding("usr_1", "github", "gh_1", "alice")));
        when(userRepo.findById("usr_1")).thenReturn(Optional.of(existing));
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleBindingRepo.findByUserId(any())).thenReturn(List.of());

        service.bindOrCreate(claims, UserStatus.ACTIVE);

        verify(eventPublisher, never()).publishEvent(any(UserActivatedEvent.class));
        verify(initialRoleGrants, never()).grantForNewUser(any(), any());
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
        verify(initialRoleGrants, never()).grantForNewUser(any(), any());
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
    void bindOrCreate_grantIsVisibleInFirstPrincipalBeforeSessionCreation() {
        OAuthClaims claims = new OAuthClaims("github", "external-1", "admin@example.com", true, "admin", Map.of());
        Role role = new Role();
        ReflectionTestUtils.setField(role, "code", "SUPER_ADMIN");
        AtomicBoolean granted = new AtomicBoolean();
        when(bindingRepo.findByProviderCodeAndSubject("github", "external-1")).thenReturn(Optional.empty());
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            granted.set(true);
            return null;
        }).when(initialRoleGrants).grantForNewUser(any(), any());
        when(roleBindingRepo.findByUserId(any())).thenAnswer(invocation -> granted.get()
                ? List.of(new UserRoleBinding(invocation.getArgument(0), role)) : List.of());

        PlatformPrincipal principal = service.bindOrCreate(claims, UserStatus.ACTIVE);

        assertThat(principal.platformRoles()).contains("SUPER_ADMIN");
        var ordered = inOrder(initialRoleGrants, roleBindingRepo);
        ordered.verify(initialRoleGrants).grantForNewUser(claims, principal.userId());
        ordered.verify(roleBindingRepo).findByUserId(principal.userId());
    }

    @Test
    void bindOrCreate_doesNotMergeWithLocalAccountSharingEmail() {
        OAuthClaims claims = new OAuthClaims("github", "external-1", "shared@example.com", true,
                "external-user", Map.of());
        when(bindingRepo.findByProviderCodeAndSubject("github", "external-1")).thenReturn(Optional.empty());
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleBindingRepo.findByUserId(any())).thenReturn(List.of());

        PlatformPrincipal principal = service.bindOrCreate(claims, UserStatus.ACTIVE);

        assertThat(principal.userId()).startsWith("usr_");
        verify(userRepo, never()).findByEmailIgnoreCase(any());
        verify(initialRoleGrants).grantForNewUser(claims, principal.userId());
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

        assertThatThrownBy(() -> service.bindOrCreate(claims, UserStatus.ACTIVE))
                .isInstanceOf(AccountDisabledException.class);
    }

    @Test
    void bindOrCreate_existingMergedUser_throwsBeforeProfileUpdate() {
        OAuthClaims claims = new OAuthClaims(
                "github", "gh_1", "attacker@example.com", true, "attacker", Map.of()
        );
        IdentityBinding binding = new IdentityBinding("usr_1", "github", "gh_1", "alice");
        UserAccount user = new UserAccount("usr_1", "alice", "alice@example.com", null);
        user.setStatus(UserStatus.MERGED);

        when(bindingRepo.findByProviderCodeAndSubject("github", "gh_1")).thenReturn(Optional.of(binding));
        when(userRepo.findById("usr_1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.bindOrCreate(claims, UserStatus.ACTIVE))
                .isInstanceOf(AccountMergedException.class);

        assertThat(user.getDisplayName()).isEqualTo("alice");
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        verify(userRepo, never()).save(any(UserAccount.class));
    }

    @Test
    void bindOrCreate_existingSystemAccount_throwsBeforeProfileUpdate() {
        OAuthClaims claims = new OAuthClaims(
                "github", "gh_1", "attacker@example.com", true, "attacker", Map.of()
        );
        IdentityBinding binding = new IdentityBinding("system_1", "github", "gh_1", "system");
        UserAccount user = UserAccount.systemAccount("system_1", "system", null, null);

        when(bindingRepo.findByProviderCodeAndSubject("github", "gh_1")).thenReturn(Optional.of(binding));
        when(userRepo.findById("system_1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.bindOrCreate(claims, UserStatus.ACTIVE))
                .isInstanceOf(SystemAccountLoginException.class);

        assertThat(user.getDisplayName()).isEqualTo("system");
        assertThat(user.getEmail()).isNull();
        verify(userRepo, never()).save(any(UserAccount.class));
    }

    @Test
    void bindOrCreate_unverifiedEmailDoesNotPopulateNewAccount() {
        OAuthClaims claims = new OAuthClaims(
                "github", "gh_1", "unverified@example.com", false, "alice", Map.of()
        );
        when(bindingRepo.findByProviderCodeAndSubject("github", "gh_1")).thenReturn(Optional.empty());
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleBindingRepo.findByUserId(any())).thenReturn(List.of());

        service.bindOrCreate(claims, UserStatus.ACTIVE);

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userRepo).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isNull();
    }

    @Test
    void bindOrCreate_unverifiedEmailDoesNotOverwriteExistingEmail() {
        OAuthClaims claims = new OAuthClaims(
                "github", "gh_1", "unverified@example.com", false, "alice", Map.of()
        );
        IdentityBinding binding = new IdentityBinding("usr_1", "github", "gh_1", "alice");
        UserAccount user = new UserAccount("usr_1", "alice", "verified@example.com", null);

        when(bindingRepo.findByProviderCodeAndSubject("github", "gh_1")).thenReturn(Optional.of(binding));
        when(userRepo.findById("usr_1")).thenReturn(Optional.of(user));
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleBindingRepo.findByUserId("usr_1")).thenReturn(List.of());

        service.bindOrCreate(claims, UserStatus.ACTIVE);

        assertThat(user.getEmail()).isEqualTo("verified@example.com");
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
    void createPendingUserIfAbsent_existingPendingBinding_throwsAccountPending() {
        OAuthClaims claims = new OAuthClaims(
                "github", "gh_1", "alice@example.com", true, "alice", Map.of()
        );
        IdentityBinding binding = new IdentityBinding("usr_1", "github", "gh_1", "alice");
        UserAccount user = new UserAccount("usr_1", "alice", "alice@example.com", null);
        user.setStatus(UserStatus.PENDING);

        when(bindingRepo.findByProviderCodeAndSubject("github", "gh_1")).thenReturn(Optional.of(binding));
        when(userRepo.findById("usr_1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.createPendingUserIfAbsent(claims))
                .isInstanceOf(AccountPendingException.class);
        verify(userRepo, never()).save(any(UserAccount.class));
        verify(bindingRepo, never()).save(any(IdentityBinding.class));
    }

    @Test
    void createPendingUserIfAbsent_existingMergedBinding_throwsAccountMerged() {
        OAuthClaims claims = new OAuthClaims(
                "github", "gh_1", "alice@example.com", true, "alice", Map.of()
        );
        IdentityBinding binding = new IdentityBinding("usr_1", "github", "gh_1", "alice");
        UserAccount user = new UserAccount("usr_1", "alice", "alice@example.com", null);
        user.setStatus(UserStatus.MERGED);

        when(bindingRepo.findByProviderCodeAndSubject("github", "gh_1")).thenReturn(Optional.of(binding));
        when(userRepo.findById("usr_1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.createPendingUserIfAbsent(claims))
                .isInstanceOf(AccountMergedException.class);
    }

    @Test
    void createPendingUserIfAbsent_existingSystemBinding_throwsSystemAccountLogin() {
        OAuthClaims claims = new OAuthClaims(
                "github", "gh_1", "alice@example.com", true, "alice", Map.of()
        );
        IdentityBinding binding = new IdentityBinding("system_1", "github", "gh_1", "system");
        UserAccount user = UserAccount.systemAccount("system_1", "system", null, null);

        when(bindingRepo.findByProviderCodeAndSubject("github", "gh_1")).thenReturn(Optional.of(binding));
        when(userRepo.findById("system_1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.createPendingUserIfAbsent(claims))
                .isInstanceOf(SystemAccountLoginException.class);
    }

    @Test
    void createPendingUserIfAbsent_unverifiedEmailDoesNotPopulateAccount() {
        OAuthClaims claims = new OAuthClaims(
                "github", "gh_1", "unverified@example.com", false, "alice", Map.of()
        );
        when(bindingRepo.findByProviderCodeAndSubject("github", "gh_1")).thenReturn(Optional.empty());
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createPendingUserIfAbsent(claims);

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userRepo).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isNull();
    }

    @Test
    void bindOrCreate_concurrentInsertReturnsWinningPrincipal() {
        OAuthClaims claims = new OAuthClaims(
                "github", "gh_1", "alice@example.com", true, "alice", Map.of()
        );
        IdentityBinding winningBinding = new IdentityBinding("usr_winner", "github", "gh_1", "alice");
        UserAccount winningUser = new UserAccount("usr_winner", "alice", "alice@example.com", null);
        winningUser.setStatus(UserStatus.ACTIVE);

        when(bindingRepo.findByProviderCodeAndSubject("github", "gh_1"))
                .thenReturn(Optional.empty(), Optional.of(winningBinding));
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bindingRepo.saveAndFlush(any(IdentityBinding.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate binding"));
        when(userRepo.findById("usr_winner")).thenReturn(Optional.of(winningUser));
        when(roleBindingRepo.findByUserId("usr_winner")).thenReturn(List.of());

        PlatformPrincipal principal = service.bindOrCreate(claims, UserStatus.ACTIVE);

        assertThat(principal.userId()).isEqualTo("usr_winner");
        assertThat(principal.platformRoles()).containsExactly("USER");
        verify(globalNamespaceMembershipService, never()).ensureMember(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void bindOrCreate_rethrowsUnexpectedIntegrityFailureWhenNoWinnerExists() {
        OAuthClaims claims = new OAuthClaims(
                "github", "gh_1", "alice@example.com", true, "alice", Map.of()
        );
        when(bindingRepo.findByProviderCodeAndSubject("github", "gh_1"))
                .thenReturn(Optional.empty(), Optional.empty());
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bindingRepo.saveAndFlush(any(IdentityBinding.class)))
                .thenThrow(new DataIntegrityViolationException("unrelated integrity failure"));

        assertThatThrownBy(() -> service.bindOrCreate(claims, UserStatus.ACTIVE))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("unrelated integrity failure");
    }

    @Test
    void createPendingUserIfAbsent_concurrentInsertUsesWinningAccountState() {
        OAuthClaims claims = new OAuthClaims(
                "github", "gh_1", "alice@example.com", true, "alice", Map.of()
        );
        IdentityBinding winningBinding = new IdentityBinding("usr_winner", "github", "gh_1", "alice");
        UserAccount winningUser = new UserAccount("usr_winner", "alice", "alice@example.com", null);
        winningUser.setStatus(UserStatus.PENDING);

        when(bindingRepo.findByProviderCodeAndSubject("github", "gh_1"))
                .thenReturn(Optional.empty(), Optional.of(winningBinding));
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bindingRepo.saveAndFlush(any(IdentityBinding.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate binding"));
        when(userRepo.findById("usr_winner")).thenReturn(Optional.of(winningUser));

        assertThatThrownBy(() -> service.createPendingUserIfAbsent(claims))
                .isInstanceOf(AccountPendingException.class);
    }

    private static final class ImmediateTransactionOperations implements TransactionOperations {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(null);
        }
    }
}
