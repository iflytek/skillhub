package com.iflytek.skillhub.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.config.SsoProperties;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import com.iflytek.skillhub.auth.sso.SsoClient;
import com.iflytek.skillhub.auth.sso.SsoIdentityService;
import com.iflytek.skillhub.auth.sso.SsoUser;
import com.iflytek.skillhub.auth.sso.TicketValidationException;
import jakarta.servlet.http.HttpSession;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class SsoLoginControllerTest {

    private static final String BASE_URL = "https://sso.example.com";
    private static final String CLIENT_URL = "https://skillhub.example.com/api/v1/auth/sso/callback";

    private SsoProperties properties;

    @Mock
    private SsoClient ssoClient;

    @Mock
    private SsoIdentityService ssoIdentityService;

    @Mock
    private PlatformSessionService platformSessionService;

    private SsoLoginController controller;

    @BeforeEach
    void setUp() {
        properties = new SsoProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setClientUrl(CLIENT_URL);
        controller = new SsoLoginController(properties, ssoClient, ssoIdentityService,
                platformSessionService);
    }

    @Test
    void ssoLogin_enabled_redirectsToSsoServer() throws Exception {
        properties.setEnabled(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.ssoLogin(null, request, response);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl())
                .startsWith("https://sso.example.com/login")
                .contains("clientUrl=" + CLIENT_URL);
    }

    @Test
    void ssoLogin_disabled_returns403() throws Exception {
        properties.setEnabled(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.ssoLogin(null, request, response);

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void ssoLogin_withReturnTo_storesInSession() throws Exception {
        properties.setEnabled(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("returnTo", "/skills/123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.ssoLogin("/skills/123", request, response);

        HttpSession session = request.getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute("ssoReturnTo")).isEqualTo("/skills/123");
    }

    @Test
    void ssoCallback_enabled_validatesTicketAndRedirects() throws Exception {
        properties.setEnabled(true);
        SsoUser ssoUser = new SsoUser("zhangsan", "EMP001", "张三");
        PlatformPrincipal principal = new PlatformPrincipal(
                "usr_001", "张三", null, null, "sso", Set.of("USER"));

        when(ssoClient.validateTicket("ST-valid")).thenReturn(ssoUser);
        when(ssoIdentityService.resolveOrCreate(ssoUser)).thenReturn(principal);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.ssoCallback("ST-valid", request, response);

        verify(platformSessionService).establishSession(principal, request);
        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }

    @Test
    void ssoCallback_withReturnTo_redirectsToSavedUrl() throws Exception {
        properties.setEnabled(true);
        SsoUser ssoUser = new SsoUser("zhangsan", "EMP001", "张三");
        PlatformPrincipal principal = new PlatformPrincipal(
                "usr_001", "张三", null, null, "sso", Set.of("USER"));

        when(ssoClient.validateTicket("ST-valid")).thenReturn(ssoUser);
        when(ssoIdentityService.resolveOrCreate(ssoUser)).thenReturn(principal);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("ssoReturnTo", "/skills/456");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.ssoCallback("ST-valid", request, response);

        assertThat(response.getRedirectedUrl()).isEqualTo("/skills/456");
        HttpSession session = request.getSession(false);
        assertThat(session.getAttribute("ssoReturnTo")).isNull();
    }

    @Test
    void ssoCallback_invalidTicket_redirectsToErrorPage() throws Exception {
        properties.setEnabled(true);
        when(ssoClient.validateTicket("ST-invalid"))
                .thenThrow(new TicketValidationException("Invalid ticket"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.ssoCallback("ST-invalid", request, response);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=sso_auth_failed");
    }

    @Test
    void ssoCallback_genericException_redirectsToErrorPage() throws Exception {
        properties.setEnabled(true);
        when(ssoClient.validateTicket("ST-error"))
                .thenThrow(new RuntimeException("Unexpected error"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.ssoCallback("ST-error", request, response);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=sso_error");
    }

    @Test
    void ssoCallback_disabled_returns403() throws Exception {
        properties.setEnabled(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.ssoCallback("ST-anything", request, response);

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void ssoLogin_ssoUrlContainsClientUrl() throws Exception {
        properties.setEnabled(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.ssoLogin(null, request, response);

        String redirectUrl = response.getRedirectedUrl();
        assertThat(redirectUrl).contains("clientUrl=" + CLIENT_URL);
        assertThat(redirectUrl).startsWith("https://sso.example.com/login");
    }
}
