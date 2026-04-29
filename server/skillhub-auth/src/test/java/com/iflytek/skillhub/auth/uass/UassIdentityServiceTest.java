package com.iflytek.skillhub.auth.uass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.entity.IdentityBinding;
import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.identity.IdentityBindingService;
import com.iflytek.skillhub.auth.oauth.AccountDisabledException;
import com.iflytek.skillhub.auth.oauth.AccountPendingException;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.namespace.GlobalNamespaceMembershipService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UassIdentityServiceTest {

    @Mock
    private IdentityBindingRepository bindingRepo;

    @Mock
    private UserAccountRepository userRepo;

    @Mock
    private UserRoleBindingRepository roleBindingRepo;

    @Mock
    private GlobalNamespaceMembershipService globalNamespaceMembershipService;

    private UassIdentityService service;

    @BeforeEach
    void setUp() {
        service = new UassIdentityService(new IdentityBindingService(
                bindingRepo,
                userRepo,
                roleBindingRepo,
                globalNamespaceMembershipService
        ));
    }

    @Test
    void resolvePrincipal_existingUserLooksUpByUserCodeAndRefreshesProfile() {
        IdentityBinding binding = new IdentityBinding("usr_1", UassIdentityService.PROVIDER_CODE, "U1001", "old-name");
        UserAccount user = new UserAccount("usr_1", "Old Name", "old@example.com", null);

        when(bindingRepo.findByProviderCodeAndSubject(UassIdentityService.PROVIDER_CODE, "U1001"))
                .thenReturn(Optional.of(binding));
        when(userRepo.findById("usr_1")).thenReturn(Optional.of(user));
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleBindingRepo.findByUserId("usr_1")).thenReturn(List.of());

        PlatformPrincipal principal = service.resolvePrincipal(
                loginContext(" U1001 "),
                userProfile(null, " Alice Zhang ", " Alice@Example.com ", Map.of("avatar_url", " https://avatar.test/a.png "))
        );

        verify(bindingRepo).findByProviderCodeAndSubject(UassIdentityService.PROVIDER_CODE, "U1001");
        assertThat(user.getDisplayName()).isEqualTo("Alice Zhang");
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getAvatarUrl()).isEqualTo("https://avatar.test/a.png");
        assertThat(principal.userId()).isEqualTo("usr_1");
        assertThat(principal.displayName()).isEqualTo("Alice Zhang");
        assertThat(principal.oauthProvider()).isEqualTo(UassIdentityService.PROVIDER_CODE);
    }

    @Test
    void resolvePrincipal_firstLoginAutoCreatesActiveUserAndBinding() {
        when(bindingRepo.findByProviderCodeAndSubject(UassIdentityService.PROVIDER_CODE, "U1002"))
                .thenReturn(Optional.empty());
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleBindingRepo.findByUserId(any())).thenReturn(List.of());

        PlatformPrincipal principal = service.resolvePrincipal(
                loginContext("U1002"),
                userProfile("U1002", "New Hire", "new.hire@example.com", Map.of("avatar_url", "https://avatar.test/new.png"))
        );

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        ArgumentCaptor<IdentityBinding> bindingCaptor = ArgumentCaptor.forClass(IdentityBinding.class);
        verify(userRepo).save(userCaptor.capture());
        verify(bindingRepo).save(bindingCaptor.capture());
        verify(globalNamespaceMembershipService).ensureMember(userCaptor.getValue().getId());
        assertThat(userCaptor.getValue().getDisplayName()).isEqualTo("New Hire");
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("new.hire@example.com");
        assertThat(userCaptor.getValue().getAvatarUrl()).isEqualTo("https://avatar.test/new.png");
        assertThat(bindingCaptor.getValue().getProviderCode()).isEqualTo(UassIdentityService.PROVIDER_CODE);
        assertThat(bindingCaptor.getValue().getSubject()).isEqualTo("U1002");
        assertThat(principal.platformRoles()).containsExactly("USER");
    }

    @Test
    void resolvePrincipal_disabledUserThrowsAccountDisabled() {
        IdentityBinding binding = new IdentityBinding("usr_2", UassIdentityService.PROVIDER_CODE, "U1003", "disabled");
        UserAccount user = new UserAccount("usr_2", "Disabled", "disabled@example.com", null);
        user.setStatus(UserStatus.DISABLED);

        when(bindingRepo.findByProviderCodeAndSubject(UassIdentityService.PROVIDER_CODE, "U1003"))
                .thenReturn(Optional.of(binding));
        when(userRepo.findById("usr_2")).thenReturn(Optional.of(user));
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.resolvePrincipal(loginContext("U1003"), userProfile("U1003", "Disabled", null, Map.of())))
                .isInstanceOf(AccountDisabledException.class);
    }

    @Test
    void resolvePrincipal_pendingUserThrowsAccountPending() {
        IdentityBinding binding = new IdentityBinding("usr_3", UassIdentityService.PROVIDER_CODE, "U1004", "pending");
        UserAccount user = new UserAccount("usr_3", "Pending", "pending@example.com", null);
        user.setStatus(UserStatus.PENDING);

        when(bindingRepo.findByProviderCodeAndSubject(UassIdentityService.PROVIDER_CODE, "U1004"))
                .thenReturn(Optional.of(binding));
        when(userRepo.findById("usr_3")).thenReturn(Optional.of(user));
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.resolvePrincipal(loginContext("U1004"), userProfile("U1004", "Pending", null, Map.of())))
                .isInstanceOf(AccountPendingException.class);
    }

    @Test
    void resolvePrincipal_missingUserCodeFailsCleanly() {
        assertThatThrownBy(() -> service.resolvePrincipal(loginContext(" "), userProfile(" ", "No Code", null, Map.of())))
                .isInstanceOf(AuthFlowException.class)
                .satisfies(exception -> {
                    AuthFlowException authFlowException = (AuthFlowException) exception;
                    assertThat(authFlowException.getStatus().value()).isEqualTo(401);
                    assertThat(authFlowException.getMessageCode()).isEqualTo("error.auth.uass.userCodeMissing");
                });

        verify(bindingRepo, never()).findByProviderCodeAndSubject(any(), any());
        verify(userRepo, never()).save(any(UserAccount.class));
    }

    @Test
    void resolvePrincipal_createFailureDoesNotLeaveBindingBehind() {
        when(bindingRepo.findByProviderCodeAndSubject(UassIdentityService.PROVIDER_CODE, "U1005"))
                .thenReturn(Optional.empty());
        when(userRepo.save(any(UserAccount.class))).thenThrow(new IllegalStateException("save failed"));

        assertThatThrownBy(() -> service.resolvePrincipal(
                loginContext("U1005"),
                userProfile("U1005", "Broken", "broken@example.com", Map.of())
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("save failed");

        verify(bindingRepo, never()).save(any(IdentityBinding.class));
        verify(globalNamespaceMembershipService, never()).ensureMember(any());
    }

    private static UassLoginContext loginContext(String userCode) {
        return new UassLoginContext(
                "state-1",
                URI.create("https://skillhub.example.com/api/v1/auth/uass/callback"),
                userCode,
                "access-token",
                null,
                null,
                Map.of()
        );
    }

    private static UassUserProfile userProfile(String userCode,
                                               String displayName,
                                               String email,
                                               Map<String, String> attributes) {
        return new UassUserProfile(
                userCode,
                displayName,
                email,
                null,
                null,
                attributes
        );
    }
}
