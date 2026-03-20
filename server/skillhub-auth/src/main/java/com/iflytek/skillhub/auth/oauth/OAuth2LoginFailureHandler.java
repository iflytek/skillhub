package com.iflytek.skillhub.auth.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Failure handler for OAuth logins that normalizes policy and account-state
 * failures into predictable user-facing redirects.
 */
@Component
public class OAuth2LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final OAuthLoginFlowService oauthLoginFlowService;

    public OAuth2LoginFailureHandler(OAuthLoginFlowService oauthLoginFlowService) {
        this.oauthLoginFlowService = oauthLoginFlowService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception)
            throws IOException, ServletException {
        String returnTo = oauthLoginFlowService.consumeReturnTo(request.getSession(false));
        String redirectTarget = oauthLoginFlowService.resolveFailureRedirect(exception, returnTo);
        if (redirectTarget != null) {
            getRedirectStrategy().sendRedirect(request, response, redirectTarget);
            return;
        }

        super.onAuthenticationFailure(request, response, exception);
    }
}
