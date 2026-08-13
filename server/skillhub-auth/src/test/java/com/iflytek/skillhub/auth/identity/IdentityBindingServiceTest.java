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
import com.iflytek.skillhub.domain.event.UserActivatedEvent;
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
import org.springframework.context.ApplicationEventPublisher;
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

    private IdentityBindingService service;

    @BeforeEach
    void setUp() {
        service = new IdentityBindingService(bindingRepo, userRepo, roleBindingRepo,
                globalNamespaceMembershipService, eventPublisher);
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
}
