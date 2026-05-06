package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.DirectLoginRequest;
import com.iflytek.skillhub.security.AuthFailureThrottleService;
import com.iflytek.skillhub.service.AuthMethodCatalog;
import com.iflytek.skillhub.service.DirectAuthService;
import com.iflytek.skillhub.service.SessionBootstrapService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.context.i18n.LocaleContextHolder.setLocale;

@ExtendWith(MockitoExtension.class)
class AuthControllerUnitTest {

    @Mock
    private AuthMethodCatalog authMethodCatalog;

    @Mock
    private SessionBootstrapService sessionBootstrapService;

    @Mock
    private DirectAuthService directAuthService;

    @Mock
    private AuthFailureThrottleService authFailureThrottleService;

    @Mock
    private UserRoleBindingRepository userRoleBindingRepository;

    @Mock
    private PlatformSessionService platformSessionService;

    @Mock
    private UserAccountRepository userAccountRepository;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("response.success.read", Locale.getDefault(), "response.success.read");
        messageSource.addMessage("response.success.update", Locale.getDefault(), "response.success.update");
        ApiResponseFactory responseFactory = new ApiResponseFactory(
                messageSource,
                Clock.fixed(Instant.parse("2026-03-19T08:00:00Z"), ZoneOffset.UTC)
        );
        controller = new AuthController(
                responseFactory,
                authMethodCatalog,
                sessionBootstrapService,
                directAuthService,
                authFailureThrottleService,
                userRoleBindingRepository,
                platformSessionService,
                userAccountRepository
        );
        setLocale(Locale.getDefault());
    }

    @Test
    void me_userNotFound_throwsUnauthorizedException() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "Tester", "tester@example.com", "", "github", Set.of("USER")
        );
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        given(userAccountRepository.findById("user-1")).willReturn(Optional.empty());
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> controller.me(principal, auth, request))
                .isInstanceOf(com.iflytek.skillhub.exception.UnauthorizedException.class)
                .hasMessage("error.auth.required");
    }

    @Test
    void me_disabledUser_invalidatesSessionAndThrowsUnauthorizedException() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "Tester", "tester@example.com", "", "github", Set.of("USER")
        );
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        UserAccount user = new UserAccount("user-1", "Tester", "tester@example.com", "");
        user.setStatus(UserStatus.DISABLED);
        given(userAccountRepository.findById("user-1")).willReturn(Optional.of(user));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession();

        assertThatThrownBy(() -> controller.me(principal, auth, request))
                .isInstanceOf(com.iflytek.skillhub.exception.UnauthorizedException.class)
                .hasMessage("error.auth.required");
    }

    @Test
    void me_avatarChanged_refreshesSessionWithNewAvatar() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "Tester", "tester@example.com", "https://old.example.com/avatar.png", "github", Set.of("USER")
        );
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        UserAccount user = new UserAccount("user-1", "Tester", "tester@example.com", "https://new.example.com/avatar.png");
        given(userAccountRepository.findById("user-1")).willReturn(Optional.of(user));
        given(userRoleBindingRepository.findByUserId("user-1")).willReturn(List.of());
        MockHttpServletRequest request = new MockHttpServletRequest();

        controller.me(principal, auth, request);

        verify(platformSessionService).establishSession(
                any(PlatformPrincipal.class), eq(request), eq(false));
    }

    @Test
    void directLogin_unauthorizedException_recordsFailureAndThrows() {
        DirectLoginRequest request = new DirectLoginRequest("local-password", "alice", "secret");
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("127.0.0.1");

        AuthFlowException ex = new AuthFlowException(HttpStatus.UNAUTHORIZED, "error.auth.invalidCredentials");
        given(directAuthService.authenticate("local-password", "alice", "secret", httpRequest))
                .willThrow(ex);

        assertThatThrownBy(() -> controller.directLogin(request, httpRequest))
                .isInstanceOf(AuthFlowException.class)
                .hasMessage("error.auth.invalidCredentials");

        verify(authFailureThrottleService).recordFailure("direct:local-password", "alice", "127.0.0.1");
    }

    @Test
    void directLogin_nonUnauthorizedException_doesNotRecordFailure() {
        DirectLoginRequest request = new DirectLoginRequest("local-password", "alice", "secret");
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("127.0.0.1");

        AuthFlowException ex = new AuthFlowException(HttpStatus.INTERNAL_SERVER_ERROR, "error.auth.serverError");
        given(directAuthService.authenticate("local-password", "alice", "secret", httpRequest))
                .willThrow(ex);

        assertThatThrownBy(() -> controller.directLogin(request, httpRequest))
                .isInstanceOf(AuthFlowException.class)
                .hasMessage("error.auth.serverError");

        verify(authFailureThrottleService, never()).recordFailure(anyString(), anyString(), anyString());
    }

    @Test
    void directLogin_successfulLogin_resetsFailureCounter() {
        DirectLoginRequest request = new DirectLoginRequest("local-password", "alice", "secret");
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("127.0.0.1");

        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "Alice", "alice@example.com", "", "local", Set.of("USER")
        );
        given(directAuthService.authenticate("local-password", "alice", "secret", httpRequest))
                .willReturn(principal);

        controller.directLogin(request, httpRequest);

        verify(authFailureThrottleService).resetIdentifier("direct:local-password", "alice");
    }

    @Test
    void me_withRoleBinding_returnsFreshRoles() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "Tester", "tester@example.com", "", "github", Set.of("USER")
        );
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        UserAccount user = new UserAccount("user-1", "Tester", "tester@example.com", "");
        given(userAccountRepository.findById("user-1")).willReturn(Optional.of(user));

        Role role = mock(Role.class);
        given(role.getCode()).willReturn("ADMIN");
        UserRoleBinding binding = mock(UserRoleBinding.class);
        given(binding.getRole()).willReturn(role);
        given(userRoleBindingRepository.findByUserId("user-1")).willReturn(List.of(binding));

        MockHttpServletRequest request = new MockHttpServletRequest();

        controller.me(principal, auth, request);

        verify(platformSessionService).establishSession(
                any(PlatformPrincipal.class), eq(request), eq(false));
    }

    @Test
    void resolveClientIp_xRealIP() throws Exception {
        java.lang.reflect.Method method = AuthController.class.getDeclaredMethod("resolveClientIp", jakarta.servlet.http.HttpServletRequest.class);
        method.setAccessible(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "192.168.1.1");

        String result = (String) method.invoke(controller, request);
        assertThat(result).isEqualTo("192.168.1.1");
    }

    @Test
    void resolveClientIp_remoteAddr() throws Exception {
        java.lang.reflect.Method method = AuthController.class.getDeclaredMethod("resolveClientIp", jakarta.servlet.http.HttpServletRequest.class);
        method.setAccessible(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");

        String result = (String) method.invoke(controller, request);
        assertThat(result).isEqualTo("10.0.0.1");
    }

    @Test
    void resolveClientIp_commaSeparatedXForwardedFor() throws Exception {
        java.lang.reflect.Method method = AuthController.class.getDeclaredMethod("resolveClientIp", jakarta.servlet.http.HttpServletRequest.class);
        method.setAccessible(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "10.0.0.2, 10.0.0.3");

        String result = (String) method.invoke(controller, request);
        assertThat(result).isEqualTo("10.0.0.2");
    }
}
