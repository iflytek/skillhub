package com.iflytek.skillhub.auth.oauth;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Login success handler that copies the resolved platform principal into the
 * HTTP session and then redirects to the stored return target.
 */
@Component
public class OAuth2LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final PlatformSessionService platformSessionService;
    private final OAuthLoginFlowService oauthLoginFlowService;

    public OAuth2LoginSuccessHandler(PlatformSessionService platformSessionService,
                                     OAuthLoginFlowService oauthLoginFlowService) {
        this.platformSessionService = platformSessionService;
        this.oauthLoginFlowService = oauthLoginFlowService;
        setDefaultTargetUrl(OAuthLoginRedirectSupport.DEFAULT_TARGET_URL);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException, ServletException {
        if (authentication.getPrincipal() instanceof OAuth2User oAuth2User) {
            PlatformPrincipal principal = (PlatformPrincipal) oAuth2User.getAttributes().get("platformPrincipal");
            if (principal != null) {
                platformSessionService.attachToAuthenticatedSession(principal, authentication, request, true);
            }
        }
        String returnTo = oauthLoginFlowService.consumeReturnTo(request.getSession(false));
        if (returnTo != null) {
            getRedirectStrategy().sendRedirect(request, response, returnTo);
            clearAuthenticationAttributes(request);
            return;
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
