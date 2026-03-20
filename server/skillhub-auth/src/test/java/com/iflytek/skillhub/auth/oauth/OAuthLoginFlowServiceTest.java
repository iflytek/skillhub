package com.iflytek.skillhub.auth.oauth;

import com.iflytek.skillhub.auth.identity.IdentityBindingService;
import com.iflytek.skillhub.auth.policy.AccessPolicy;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OAuthLoginFlowServiceTest {

    @Test
    void rememberReturnTo_stores_sanitized_return_target() {
        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(),
                mock(AccessPolicy.class),
                mock(IdentityBindingService.class)
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("returnTo", "/dashboard/publish");

        service.rememberReturnTo(request);

        HttpSession session = request.getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE))
                .isEqualTo("/dashboard/publish");
    }

    @Test
    void resolveFailureRedirect_maps_access_denied_to_user_facing_page() {
        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(),
                mock(AccessPolicy.class),
                mock(IdentityBindingService.class)
        );

        String redirect = service.resolveFailureRedirect(
                new OAuth2AuthenticationException(new OAuth2Error("access_denied")),
                "/settings/accounts"
        );

        assertThat(redirect).isEqualTo("/access-denied");
    }

    @Test
    void consumeReturnTo_clearsUnsafeSessionValue() {
        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(),
                mock(AccessPolicy.class),
                mock(IdentityBindingService.class)
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpSession session = request.getSession(true);
        session.setAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE, "https://evil.example");

        String returnTo = service.consumeReturnTo(session);

        assertThat(returnTo).isNull();
        assertThat(session.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE)).isNull();
    }
}
