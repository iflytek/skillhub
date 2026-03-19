package com.iflytek.skillhub.auth.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Failure handler for OAuth logins that normalizes policy and account-state
 * failures into predictable user-facing redirects.
 */
@Component
public class OAuth2LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception)
            throws IOException, ServletException {
        String returnTo = consumeReturnTo(request.getSession(false));
        if (exception instanceof AccountPendingException) {
            getRedirectStrategy().sendRedirect(request, response, "/pending-approval");
            return;
        }
        if (exception instanceof AccountDisabledException) {
            getRedirectStrategy().sendRedirect(request, response, "/access-denied");
            return;
        }
        if (exception instanceof org.springframework.security.oauth2.core.OAuth2AuthenticationException oauth2Exception
                && "access_denied".equals(oauth2Exception.getError().getErrorCode())) {
            getRedirectStrategy().sendRedirect(request, response, "/access-denied");
            return;
        }

        if (returnTo != null) {
            getRedirectStrategy().sendRedirect(
                    request,
                    response,
                    "/login?returnTo=" + URLEncoder.encode(returnTo, StandardCharsets.UTF_8)
            );
            return;
        }

        super.onAuthenticationFailure(request, response, exception);
    }

    private String consumeReturnTo(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE);
        session.removeAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE);
        return value instanceof String str ? OAuthLoginRedirectSupport.sanitizeReturnTo(str) : null;
    }
}
