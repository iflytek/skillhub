package com.iflytek.skillhub.auth.uass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.entity.IdentityBinding;
import com.iflytek.skillhub.auth.identity.IdentityBindingService;
import com.iflytek.skillhub.auth.oauth.AccountDisabledException;
import com.iflytek.skillhub.auth.oauth.AccountPendingException;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import com.iflytek.skillhub.auth.repository.RoleRepository;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import com.iflytek.skillhub.auth.uass.store.UassLoginState;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@ExtendWith(MockitoExtension.class)
class UassCallbackFlowServiceTest {

    @Mock
    private UassClientFacade uassClientFacade;

    @Mock
    private UassLoginStateService uassLoginStateService;

    @Mock
    private IdentityBindingRepository bindingRepo;

    @Mock
    private UserAccountRepository userRepo;

    @Mock
    private UserRoleBindingRepository roleBindingRepo;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private GlobalNamespaceMembershipService globalNamespaceMembershipService;

    private UassCallbackFlowService service;

    @BeforeEach
    void setUp() {
        UassIdentityService uassIdentityService = new UassIdentityService(
                new IdentityBindingService(
                        bindingRepo,
                        userRepo,
                        roleBindingRepo,
                        globalNamespaceMembershipService
                ),
                new UassBootstrapAdminRoleService(roleBindingRepo, roleRepository, new UassProperties())
        );
        service = new UassCallbackFlowService(
                uassClientFacade,
                uassLoginStateService,
                uassIdentityService,
                new PlatformSessionService(),
                new UassSessionContextService(),
                (URI) null
        );
    }

    @Test
    void completeLogin_existingUserRefreshesProfileAndEstablishesSession() {
        IdentityBinding binding = new IdentityBinding("usr_1", UassIdentityService.PROVIDER_CODE, "U1001", "old-name");
        UserAccount user = new UserAccount("usr_1", "Old Name", "old@example.com", null);
        MockHttpServletRequest request = callbackRequest();

        when(uassLoginStateService.consumeForCallback("state-1"))
                .thenReturn(Optional.of(new UassLoginState("/dashboard/review", java.time.Instant.now(), "uass", null)));
        when(uassClientFacade.validateLogin("auth-code", "state-1", CALLBACK_URI))
                .thenReturn(loginContext("U1001"));
        when(uassClientFacade.loadUserProfile(any()))
                .thenReturn(userProfile(null, " Alice Zhang ", " Alice@Example.com ", Map.of("avatar_url", " https://avatar.test/a.png ")));
        when(bindingRepo.findByProviderCodeAndSubject(UassIdentityService.PROVIDER_CODE, "U1001"))
                .thenReturn(Optional.of(binding));
        when(userRepo.findById("usr_1")).thenReturn(Optional.of(user));
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleBindingRepo.findByUserId("usr_1")).thenReturn(List.of());

        String redirectTo = service.completeLogin("auth-code", "state-1", CALLBACK_URI, request);

        assertThat(redirectTo).isEqualTo("/dashboard/review");
        assertThat(user.getDisplayName()).isEqualTo("Alice Zhang");
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getAvatarUrl()).isEqualTo("https://avatar.test/a.png");
        assertThat(request.getSession(false)).isNotNull();
        assertThat(request.getSession(false).getAttribute("platformPrincipal")).isNotNull();
        assertThat(request.getSession(false).getAttribute(UassSessionContextService.SESSION_ATTRIBUTE)).isNotNull();
        assertThat(request.getSession(false)
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNotNull();
    }

    @Test
    void completeLogin_firstLoginAutoCreatesUserAndContinuesSameSessionFlow() {
        MockHttpServletRequest request = callbackRequest();

        when(uassLoginStateService.consumeForCallback("state-1"))
                .thenReturn(Optional.of(new UassLoginState("/dashboard", java.time.Instant.now(), "uass", null)));
        when(uassClientFacade.validateLogin("auth-code", "state-1", CALLBACK_URI))
                .thenReturn(loginContext("U1002"));
        when(uassClientFacade.loadUserProfile(any()))
                .thenReturn(userProfile("U1002", "New Hire", "new.hire@example.com", Map.of("avatar_url", "https://avatar.test/new.png")));
        when(bindingRepo.findByProviderCodeAndSubject(UassIdentityService.PROVIDER_CODE, "U1002"))
                .thenReturn(Optional.empty());
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleBindingRepo.findByUserId(any())).thenReturn(List.of());

        String redirectTo = service.completeLogin("auth-code", "state-1", CALLBACK_URI, request);

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        ArgumentCaptor<IdentityBinding> bindingCaptor = ArgumentCaptor.forClass(IdentityBinding.class);
        verify(userRepo).save(userCaptor.capture());
        verify(bindingRepo).save(bindingCaptor.capture());
        verify(globalNamespaceMembershipService).ensureMember(userCaptor.getValue().getId());
        assertThat(redirectTo).isEqualTo("/dashboard");
        assertThat(userCaptor.getValue().getDisplayName()).isEqualTo("New Hire");
        assertThat(bindingCaptor.getValue().getSubject()).isEqualTo("U1002");
        assertThat(request.getSession(false)).isNotNull();
        assertThat(request.getSession(false).getAttribute("platformPrincipal")).isNotNull();
    }

    @Test
    void completeLogin_disabledUserDoesNotLeaveSessionBehind() {
        IdentityBinding binding = new IdentityBinding("usr_2", UassIdentityService.PROVIDER_CODE, "U1003", "disabled");
        UserAccount user = new UserAccount("usr_2", "Disabled", "disabled@example.com", null);
        user.setStatus(UserStatus.DISABLED);
        MockHttpServletRequest request = callbackRequest();

        when(uassLoginStateService.consumeForCallback("state-1"))
                .thenReturn(Optional.of(new UassLoginState("/dashboard", java.time.Instant.now(), "uass", null)));
        when(uassClientFacade.validateLogin("auth-code", "state-1", CALLBACK_URI))
                .thenReturn(loginContext("U1003"));
        when(uassClientFacade.loadUserProfile(any()))
                .thenReturn(userProfile("U1003", "Disabled", null, Map.of()));
        when(bindingRepo.findByProviderCodeAndSubject(UassIdentityService.PROVIDER_CODE, "U1003"))
                .thenReturn(Optional.of(binding));
        when(userRepo.findById("usr_2")).thenReturn(Optional.of(user));
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.completeLogin("auth-code", "state-1", CALLBACK_URI, request))
                .isInstanceOf(AccountDisabledException.class);

        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void completeLogin_pendingUserDoesNotLeaveSessionBehind() {
        IdentityBinding binding = new IdentityBinding("usr_3", UassIdentityService.PROVIDER_CODE, "U1004", "pending");
        UserAccount user = new UserAccount("usr_3", "Pending", "pending@example.com", null);
        user.setStatus(UserStatus.PENDING);
        MockHttpServletRequest request = callbackRequest();

        when(uassLoginStateService.consumeForCallback("state-1"))
                .thenReturn(Optional.of(new UassLoginState("/dashboard", java.time.Instant.now(), "uass", null)));
        when(uassClientFacade.validateLogin("auth-code", "state-1", CALLBACK_URI))
                .thenReturn(loginContext("U1004"));
        when(uassClientFacade.loadUserProfile(any()))
                .thenReturn(userProfile("U1004", "Pending", null, Map.of()));
        when(bindingRepo.findByProviderCodeAndSubject(UassIdentityService.PROVIDER_CODE, "U1004"))
                .thenReturn(Optional.of(binding));
        when(userRepo.findById("usr_3")).thenReturn(Optional.of(user));
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.completeLogin("auth-code", "state-1", CALLBACK_URI, request))
                .isInstanceOf(AccountPendingException.class);

        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void completeLogin_missingUserCodeFailsWithoutCreatingSession() {
        MockHttpServletRequest request = callbackRequest();

        when(uassLoginStateService.consumeForCallback("state-1"))
                .thenReturn(Optional.of(new UassLoginState("/dashboard", java.time.Instant.now(), "uass", null)));
        when(uassClientFacade.validateLogin("auth-code", "state-1", CALLBACK_URI))
                .thenReturn(loginContext(" "));
        when(uassClientFacade.loadUserProfile(any()))
                .thenReturn(userProfile(" ", "No Code", null, Map.of()));

        assertThatThrownBy(() -> service.completeLogin("auth-code", "state-1", CALLBACK_URI, request))
                .isInstanceOf(com.iflytek.skillhub.auth.exception.AuthFlowException.class);

        verify(bindingRepo, never()).findByProviderCodeAndSubject(any(), any());
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void completeLogin_createFailureDoesNotLeaveSessionBehind() {
        MockHttpServletRequest request = callbackRequest();

        when(uassLoginStateService.consumeForCallback("state-1"))
                .thenReturn(Optional.of(new UassLoginState("/dashboard", java.time.Instant.now(), "uass", null)));
        when(uassClientFacade.validateLogin("auth-code", "state-1", CALLBACK_URI))
                .thenReturn(loginContext("U1005"));
        when(uassClientFacade.loadUserProfile(any()))
                .thenReturn(userProfile("U1005", "Broken", "broken@example.com", Map.of()));
        when(bindingRepo.findByProviderCodeAndSubject(UassIdentityService.PROVIDER_CODE, "U1005"))
                .thenReturn(Optional.empty());
        when(userRepo.save(any(UserAccount.class))).thenThrow(new IllegalStateException("save failed"));

        assertThatThrownBy(() -> service.completeLogin("auth-code", "state-1", CALLBACK_URI, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("save failed");

        verify(bindingRepo, never()).save(any(IdentityBinding.class));
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void completeLogin_usesConfiguredPublicBaseUrlForRelativeReturnTo() {
        UassIdentityService uassIdentityService = new UassIdentityService(
                new IdentityBindingService(
                        bindingRepo,
                        userRepo,
                        roleBindingRepo,
                        globalNamespaceMembershipService
                ),
                new UassBootstrapAdminRoleService(roleBindingRepo, roleRepository, new UassProperties())
        );
        UassCallbackFlowService serviceWithPublicBase = new UassCallbackFlowService(
                uassClientFacade,
                uassLoginStateService,
                uassIdentityService,
                new PlatformSessionService(),
                new UassSessionContextService(),
                "http://localhost:3000"
        );
        MockHttpServletRequest request = callbackRequest();

        when(uassLoginStateService.consumeForCallback("state-1"))
                .thenReturn(Optional.of(new UassLoginState("/dashboard/reviews?tab=pending#panel", java.time.Instant.now(), "uass", null)));
        when(uassClientFacade.validateLogin("auth-code", "state-1", CALLBACK_URI))
                .thenReturn(loginContext("U1006"));
        when(uassClientFacade.loadUserProfile(any()))
                .thenReturn(userProfile("U1006", "Browser User", "browser@example.com", Map.of()));
        when(bindingRepo.findByProviderCodeAndSubject(UassIdentityService.PROVIDER_CODE, "U1006"))
                .thenReturn(Optional.empty());
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleBindingRepo.findByUserId(any())).thenReturn(List.of());

        String redirectTo = serviceWithPublicBase.completeLogin("auth-code", "state-1", CALLBACK_URI, request);

        assertThat(redirectTo).isEqualTo("http://localhost:3000/dashboard/reviews?tab=pending#panel");
    }

    @Test
    void completeLogin_blankPublicBaseUrlKeepsRelativeReturnToUntouched() {
        UassIdentityService uassIdentityService = new UassIdentityService(
                new IdentityBindingService(
                        bindingRepo,
                        userRepo,
                        roleBindingRepo,
                        globalNamespaceMembershipService
                ),
                new UassBootstrapAdminRoleService(roleBindingRepo, roleRepository, new UassProperties())
        );
        UassCallbackFlowService serviceWithBlankPublicBase = new UassCallbackFlowService(
                uassClientFacade,
                uassLoginStateService,
                uassIdentityService,
                new PlatformSessionService(),
                new UassSessionContextService(),
                "   "
        );
        MockHttpServletRequest request = callbackRequest();

        when(uassLoginStateService.consumeForCallback("state-1"))
                .thenReturn(Optional.of(new UassLoginState("/dashboard", java.time.Instant.now(), "uass", null)));
        when(uassClientFacade.validateLogin("auth-code", "state-1", CALLBACK_URI))
                .thenReturn(loginContext("U1009"));
        when(uassClientFacade.loadUserProfile(any()))
                .thenReturn(userProfile("U1009", "Blank Base", "blank@example.com", Map.of()));
        when(bindingRepo.findByProviderCodeAndSubject(UassIdentityService.PROVIDER_CODE, "U1009"))
                .thenReturn(Optional.empty());
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleBindingRepo.findByUserId(any())).thenReturn(List.of());

        String redirectTo = serviceWithBlankPublicBase.completeLogin("auth-code", "state-1", CALLBACK_URI, request);

        assertThat(redirectTo).isEqualTo("/dashboard");
    }

    @Test
    void completeLogin_rejectsMissingLoginState() {
        MockHttpServletRequest request = callbackRequest();
        when(uassLoginStateService.consumeForCallback("state-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeLogin("auth-code", "state-1", CALLBACK_URI, request))
                .isInstanceOf(com.iflytek.skillhub.auth.exception.AuthFlowException.class);

        verify(uassClientFacade, never()).validateLogin(any(), any(), any());
    }

    @Test
    void completeLogin_invalidatesEstablishedSessionWhenUassContextBindingFails() {
        UassSessionContextService failingSessionContext = new UassSessionContextService() {
            @Override
            public void bind(UassLoginContext loginContext, jakarta.servlet.http.HttpServletRequest request) {
                super.bind(loginContext, request);
                throw new IllegalStateException("bind failed");
            }
        };
        UassIdentityService uassIdentityService = new UassIdentityService(
                new IdentityBindingService(
                        bindingRepo,
                        userRepo,
                        roleBindingRepo,
                        globalNamespaceMembershipService
                ),
                new UassBootstrapAdminRoleService(roleBindingRepo, roleRepository, new UassProperties())
        );
        UassCallbackFlowService serviceWithFailingBind = new UassCallbackFlowService(
                uassClientFacade,
                uassLoginStateService,
                uassIdentityService,
                new PlatformSessionService(),
                failingSessionContext,
                (URI) null
        );
        MockHttpServletRequest request = callbackRequest();

        when(uassLoginStateService.consumeForCallback("state-1"))
                .thenReturn(Optional.of(new UassLoginState("/dashboard", java.time.Instant.now(), "uass", null)));
        when(uassClientFacade.validateLogin("auth-code", "state-1", CALLBACK_URI))
                .thenReturn(loginContext("U1007"));
        when(uassClientFacade.loadUserProfile(any()))
                .thenReturn(userProfile("U1007", "Broken Bind", "bind@example.com", Map.of()));
        when(bindingRepo.findByProviderCodeAndSubject(UassIdentityService.PROVIDER_CODE, "U1007"))
                .thenReturn(Optional.empty());
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleBindingRepo.findByUserId(any())).thenReturn(List.of());

        assertThatThrownBy(() -> serviceWithFailingBind.completeLogin("auth-code", "state-1", CALLBACK_URI, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("bind failed");

        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void completeLogin_keepsAbsoluteReturnToUnchanged() {
        UassIdentityService uassIdentityService = new UassIdentityService(
                new IdentityBindingService(
                        bindingRepo,
                        userRepo,
                        roleBindingRepo,
                        globalNamespaceMembershipService
                ),
                new UassBootstrapAdminRoleService(roleBindingRepo, roleRepository, new UassProperties())
        );
        UassCallbackFlowService serviceWithPublicBase = new UassCallbackFlowService(
                uassClientFacade,
                uassLoginStateService,
                uassIdentityService,
                new PlatformSessionService(),
                new UassSessionContextService(),
                "http://localhost:3000"
        );
        MockHttpServletRequest request = callbackRequest();

        when(uassLoginStateService.consumeForCallback("state-1"))
                .thenReturn(Optional.of(new UassLoginState("https://portal.example.com/dashboard", java.time.Instant.now(), "uass", null)));
        when(uassClientFacade.validateLogin("auth-code", "state-1", CALLBACK_URI))
                .thenReturn(loginContext("U1008"));
        when(uassClientFacade.loadUserProfile(any()))
                .thenReturn(userProfile("U1008", "Portal User", "portal@example.com", Map.of()));
        when(bindingRepo.findByProviderCodeAndSubject(UassIdentityService.PROVIDER_CODE, "U1008"))
                .thenReturn(Optional.empty());
        when(userRepo.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleBindingRepo.findByUserId(any())).thenReturn(List.of());

        String redirectTo = serviceWithPublicBase.completeLogin("auth-code", "state-1", CALLBACK_URI, request);

        assertThat(redirectTo).isEqualTo("https://portal.example.com/dashboard");
    }

    private static MockHttpServletRequest callbackRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/uass/callback");
        request.setServerName("localhost");
        request.setServerPort(80);
        request.setScheme("http");
        request.setRequestURI("/api/v1/auth/uass/callback");
        return request;
    }

    private static UassLoginContext loginContext(String userCode) {
        return new UassLoginContext(
                "state-1",
                CALLBACK_URI,
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

    private static final URI CALLBACK_URI = URI.create("http://localhost/api/v1/auth/uass/callback");
}
