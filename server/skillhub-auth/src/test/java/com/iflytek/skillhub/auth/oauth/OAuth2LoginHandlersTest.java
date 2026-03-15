package com.iflytek.skillhub.auth.oauth;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2LoginHandlersTest {

    @Test
    void successHandler_redirectsToStoredReturnTo() throws Exception {
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(new com.iflytek.skillhub.auth.session.PlatformSessionService());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        HttpSession session = request.getSession(true);
        String originalSessionId = session.getId();
        session.setAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE, "/dashboard/publish");

        var principal = new com.iflytek.skillhub.auth.rbac.PlatformPrincipal(
                "user-1", "User", "user@example.com", null, "github", Set.of()
        );
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new DefaultOAuth2User(List.of(), Map.of("platformPrincipal", principal, "login", "user"), "login"),
                null,
                List.of()
        );

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("/dashboard/publish");
        assertThat(request.getSession(false).getId()).isNotEqualTo(originalSessionId);
        assertThat(session.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE)).isNull();
        assertThat(session.getAttribute("platformPrincipal")).isEqualTo(principal);
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNotNull();
    }

    @Test
    void failureHandler_redirectsBackToLoginWithReturnTo() throws Exception {
        OAuth2LoginFailureHandler handler = new OAuth2LoginFailureHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        HttpSession session = request.getSession(true);
        session.setAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE, "/settings/accounts");

        handler.onAuthenticationFailure(
                request,
                response,
                new OAuth2AuthenticationException(new OAuth2Error("invalid_request"))
        );

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?returnTo=%2Fsettings%2Faccounts");
        assertThat(session.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE)).isNull();
    }
}
