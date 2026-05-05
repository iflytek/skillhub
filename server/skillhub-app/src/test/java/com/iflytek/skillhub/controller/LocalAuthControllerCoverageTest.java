package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.local.LocalAuthService;
import com.iflytek.skillhub.auth.local.PasswordResetService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.metrics.SkillHubMetrics;
import com.iflytek.skillhub.security.AuthFailureThrottleService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LocalAuthControllerCoverageTest {

    private final LocalAuthService localAuthService = mock(LocalAuthService.class);
    private final SkillHubMetrics skillHubMetrics = mock(SkillHubMetrics.class);
    private final PlatformSessionService platformSessionService = mock(PlatformSessionService.class);
    private final AuthFailureThrottleService authFailureThrottleService = mock(AuthFailureThrottleService.class);
    private final PasswordResetService passwordResetService = mock(PasswordResetService.class);
    private final ApiResponseFactory responseFactory = mock(ApiResponseFactory.class);

    private final LocalAuthController controller = new LocalAuthController(
            responseFactory, localAuthService, skillHubMetrics, platformSessionService,
            authFailureThrottleService, passwordResetService
    );

    @Test
    void resolveClientIp_withXForwardedFor() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2");
        String ip = (String) ReflectionTestUtils.invokeMethod(controller, "resolveClientIp", request);
        assertThat(ip).isEqualTo("10.0.0.1");
    }

    @Test
    void resolveClientIp_withUnknownForwardedFor() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
        when(request.getHeader("X-Real-IP")).thenReturn("10.0.0.3");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        String ip = (String) ReflectionTestUtils.invokeMethod(controller, "resolveClientIp", request);
        assertThat(ip).isEqualTo("10.0.0.3");
    }

    @Test
    void resolveClientIp_withRemoteAddr() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        String ip = (String) ReflectionTestUtils.invokeMethod(controller, "resolveClientIp", request);
        assertThat(ip).isEqualTo("192.168.1.1");
    }

    @Test
    void login_withRuntimeException_recordsFailureAndRethrows() {
        com.iflytek.skillhub.dto.LocalLoginRequest request = new com.iflytek.skillhub.dto.LocalLoginRequest("alice", "wrong");
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        when(localAuthService.login("alice", "wrong")).thenThrow(new RuntimeException("db error"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
                controller.login(request, httpRequest)
        );
    }
}
