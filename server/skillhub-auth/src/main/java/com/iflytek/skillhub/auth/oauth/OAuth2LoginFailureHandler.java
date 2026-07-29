package com.iflytek.skillhub.auth.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * Failure handler for OAuth logins that normalizes policy and account-state
 * failures into predictable user-facing redirects.
 */
@Component
public class OAuth2LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginFailureHandler.class);

    private final OAuthLoginFlowService oauthLoginFlowService;

    public OAuth2LoginFailureHandler(OAuthLoginFlowService oauthLoginFlowService) {
        this.oauthLoginFlowService = oauthLoginFlowService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception)
            throws IOException, ServletException {
        String errorCode = exception instanceof OAuth2AuthenticationException oauth2Exception
                ? oauth2Exception.getError().getErrorCode()
                : "unknown";
        log.error(
                "OAuth2 login failed: path={}, type={}, errorCode={}",
                request.getRequestURI(),
                exception.getClass().getSimpleName(),
                errorCode);

        String returnTo = oauthLoginFlowService.consumeReturnTo(request.getSession(false));
        String redirectTarget = oauthLoginFlowService.resolveFailureRedirect(exception, returnTo);
        if (redirectTarget != null) {
            getRedirectStrategy().sendRedirect(request, response, redirectTarget);
            return;
        }

        super.onAuthenticationFailure(request, response, exception);
    }
}
