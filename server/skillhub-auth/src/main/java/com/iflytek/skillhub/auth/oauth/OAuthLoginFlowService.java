package com.iflytek.skillhub.auth.oauth;

import com.iflytek.skillhub.auth.identity.IdentityBindingService;
import com.iflytek.skillhub.auth.policy.AccessDecision;
import com.iflytek.skillhub.auth.policy.AccessPolicy;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.user.UserStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * Flow owner for browser OAuth login. It centralizes the stages of remembering
 * the return target, loading provider claims, evaluating access policy,
 * provisioning a platform principal, and resolving the final redirect target.
 */
@Service
public class OAuthLoginFlowService {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final Map<String, OAuthClaimsExtractor> extractors;
    private final AccessPolicy accessPolicy;
    private final IdentityBindingService identityBindingService;

    public OAuthLoginFlowService(List<OAuthClaimsExtractor> extractorList,
                                 AccessPolicy accessPolicy,
                                 IdentityBindingService identityBindingService) {
        this.extractors = extractorList.stream()
                .collect(Collectors.toMap(OAuthClaimsExtractor::getProvider, Function.identity()));
        this.accessPolicy = accessPolicy;
        this.identityBindingService = identityBindingService;
    }

    public AuthenticatedLoginContext loadLoginContext(OAuth2UserRequest request) {
        OAuth2User upstreamUser = delegate.loadUser(request);
        String registrationId = request.getClientRegistration().getRegistrationId();

        OAuthClaimsExtractor extractor = extractors.get(registrationId);
        if (extractor == null) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("unsupported_provider", "Unsupported: " + registrationId, null)
            );
        }

        OAuthClaims claims = extractor.extract(request, upstreamUser);
        PlatformPrincipal principal = authenticate(claims);
        return new AuthenticatedLoginContext(upstreamUser, principal);
    }

    public PlatformPrincipal authenticate(OAuthClaims claims) {
        AccessDecision decision = accessPolicy.evaluate(claims);

        if (decision == AccessDecision.PENDING_APPROVAL) {
            identityBindingService.createPendingUserIfAbsent(claims);
            throw new AccountPendingException();
        }
        if (decision == AccessDecision.DENY) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("access_denied", "Access denied by policy", null)
            );
        }

        return identityBindingService.bindOrCreate(claims, UserStatus.ACTIVE);
    }

    public void rememberReturnTo(HttpServletRequest request) {
        String returnTo = OAuthLoginRedirectSupport.sanitizeReturnTo(request.getParameter("returnTo"));
        HttpSession session = request.getSession();
        if (returnTo == null) {
            session.removeAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE);
            return;
        }
        session.setAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE, returnTo);
    }

    public String consumeReturnTo(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE);
        session.removeAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE);
        return value instanceof String str ? OAuthLoginRedirectSupport.sanitizeReturnTo(str) : null;
    }

    public String resolveFailureRedirect(AuthenticationException exception, String returnTo) {
        if (exception instanceof AccountPendingException) {
            return "/pending-approval";
        }
        if (exception instanceof AccountDisabledException) {
            return "/access-denied";
        }
        if (exception instanceof OAuth2AuthenticationException oauth2Exception
                && "access_denied".equals(oauth2Exception.getError().getErrorCode())) {
            return "/access-denied";
        }
        if (returnTo != null) {
            return "/login?returnTo=" + URLEncoder.encode(returnTo, StandardCharsets.UTF_8);
        }
        return null;
    }

    public record AuthenticatedLoginContext(OAuth2User upstreamUser, PlatformPrincipal principal) {
    }
}
