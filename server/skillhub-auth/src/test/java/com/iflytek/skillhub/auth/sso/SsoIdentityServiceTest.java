package com.iflytek.skillhub.auth.sso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.entity.IdentityBinding;
import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.namespace.GlobalNamespaceMembershipService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SsoIdentityServiceTest {

    private static final String PROVIDER_CODE = "sso";
    private static final String SSO_ACCOUNT = "zhangsan";
    private static final String SSO_ID = "EMP001";
    private static final String SSO_NAME = "张三";

    @Mock
    private IdentityBindingRepository bindingRepo;

    @Mock
    private UserAccountRepository userRepo;

    @Mock
    private UserRoleBindingRepository roleBindingRepo;

    @Mock
    private GlobalNamespaceMembershipService globalNamespaceMembershipService;

    private SsoIdentityService service;

    @BeforeEach
    void setUp() {
        service = new SsoIdentityService(bindingRepo, userRepo, roleBindingRepo,
                globalNamespaceMembershipService);
    }

    @Test
    void resolveOrCreate_existingBinding_updatesDisplayName() {
        SsoUser ssoUser = new SsoUser(SSO_ACCOUNT, SSO_ID, "张三（更新）");
        IdentityBinding binding = new IdentityBinding("usr_001", PROVIDER_CODE, SSO_ID, SSO_ACCOUNT);
        UserAccount existingUser = new UserAccount("usr_001", SSO_NAME, null, null);
        existingUser.setStatus(UserStatus.ACTIVE);

        when(bindingRepo.findByProviderCodeAndSubject(PROVIDER_CODE, SSO_ID))
                .thenReturn(Optional.of(binding));
        when(userRepo.findById("usr_001")).thenReturn(Optional.of(existingUser));
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleBindingRepo.findByUserId("usr_001")).thenReturn(List.of());

        PlatformPrincipal principal = service.resolveOrCreate(ssoUser);

        assertThat(principal.displayName()).isEqualTo("张三（更新）");
        assertThat(principal.oauthProvider()).isEqualTo(PROVIDER_CODE);
        verify(userRepo).save(existingUser);
        verify(globalNamespaceMembershipService, never()).ensureMember(any());
    }

    @Test
    void resolveOrCreate_newSsoUser_createsUserAndBinding() {
        SsoUser ssoUser = new SsoUser(SSO_ACCOUNT, SSO_ID, SSO_NAME);

        when(bindingRepo.findByProviderCodeAndSubject(PROVIDER_CODE, SSO_ID))
                .thenReturn(Optional.empty());
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleBindingRepo.findByUserId(any())).thenReturn(List.of());

        PlatformPrincipal principal = service.resolveOrCreate(ssoUser);

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userRepo).save(userCaptor.capture());
        UserAccount createdUser = userCaptor.getValue();
        assertThat(createdUser.getId()).startsWith("usr_");
        assertThat(createdUser.getDisplayName()).isEqualTo(SSO_NAME);
        assertThat(createdUser.getStatus()).isEqualTo(UserStatus.ACTIVE);

        ArgumentCaptor<IdentityBinding> bindingCaptor = ArgumentCaptor.forClass(IdentityBinding.class);
        verify(bindingRepo).save(bindingCaptor.capture());
        IdentityBinding createdBinding = bindingCaptor.getValue();
        assertThat(createdBinding.getProviderCode()).isEqualTo(PROVIDER_CODE);
        assertThat(createdBinding.getSubject()).isEqualTo(SSO_ID);
        assertThat(createdBinding.getLoginName()).isEqualTo(SSO_ACCOUNT);

        verify(globalNamespaceMembershipService).ensureMember(createdUser.getId());
        assertThat(principal.displayName()).isEqualTo(SSO_NAME);
        assertThat(principal.oauthProvider()).isEqualTo(PROVIDER_CODE);
    }

    @Test
    void resolveOrCreate_existingUserWithExplicitRoles() {
        SsoUser ssoUser = new SsoUser(SSO_ACCOUNT, SSO_ID, SSO_NAME);
        IdentityBinding binding = new IdentityBinding("usr_001", PROVIDER_CODE, SSO_ID, SSO_ACCOUNT);
        UserAccount existingUser = new UserAccount("usr_001", SSO_NAME, null, null);
        existingUser.setStatus(UserStatus.ACTIVE);

        Role role = new Role();
        ReflectionTestUtils.setField(role, "code", "AUDITOR");

        when(bindingRepo.findByProviderCodeAndSubject(PROVIDER_CODE, SSO_ID))
                .thenReturn(Optional.of(binding));
        when(userRepo.findById("usr_001")).thenReturn(Optional.of(existingUser));
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleBindingRepo.findByUserId("usr_001"))
                .thenReturn(List.of(new UserRoleBinding("usr_001", role)));

        PlatformPrincipal principal = service.resolveOrCreate(ssoUser);

        assertThat(principal.platformRoles()).contains("AUDITOR");
    }

    @Test
    void resolveOrCreate_defaultsToUserRole() {
        SsoUser ssoUser = new SsoUser(SSO_ACCOUNT, SSO_ID, SSO_NAME);

        when(bindingRepo.findByProviderCodeAndSubject(PROVIDER_CODE, SSO_ID))
                .thenReturn(Optional.empty());
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleBindingRepo.findByUserId(any())).thenReturn(List.of());

        PlatformPrincipal principal = service.resolveOrCreate(ssoUser);

        assertThat(principal.platformRoles()).contains("USER");
    }

    @Test
    void resolveOrCreate_nonActiveUser_throwsException() {
        SsoUser ssoUser = new SsoUser(SSO_ACCOUNT, SSO_ID, SSO_NAME);
        IdentityBinding binding = new IdentityBinding("usr_001", PROVIDER_CODE, SSO_ID, SSO_ACCOUNT);
        UserAccount disabledUser = new UserAccount("usr_001", SSO_NAME, null, null);
        disabledUser.setStatus(UserStatus.DISABLED);

        when(bindingRepo.findByProviderCodeAndSubject(PROVIDER_CODE, SSO_ID))
                .thenReturn(Optional.of(binding));
        when(userRepo.findById("usr_001")).thenReturn(Optional.of(disabledUser));
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.resolveOrCreate(ssoUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void resolveOrCreate_bindingWithoutUser_throwsException() {
        SsoUser ssoUser = new SsoUser(SSO_ACCOUNT, SSO_ID, SSO_NAME);
        IdentityBinding binding = new IdentityBinding("usr_ghost", PROVIDER_CODE, SSO_ID, SSO_ACCOUNT);

        when(bindingRepo.findByProviderCodeAndSubject(PROVIDER_CODE, SSO_ID))
                .thenReturn(Optional.of(binding));
        when(userRepo.findById("usr_ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveOrCreate(ssoUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User not found for binding");
    }
}
