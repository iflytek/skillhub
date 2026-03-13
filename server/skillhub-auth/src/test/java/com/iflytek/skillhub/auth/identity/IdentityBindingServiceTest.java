package com.iflytek.skillhub.auth.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.entity.IdentityBinding;
import com.iflytek.skillhub.auth.oauth.OAuthClaims;
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
        when(roleBindingRepo.findByUserId(any())).thenReturn(List.of());

        service.bindOrCreate(claims, UserStatus.PENDING);

        verify(globalNamespaceMembershipService, never()).ensureMember(any());
    }
}
