package com.iflytek.skillhub.auth.oauth;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * Spring Security OIDC user-service bridge that normalizes standard OIDC
 * claims and reuses the existing OAuth login policy and identity binding flow.
 */
@Service
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OAuthLoginFlowService oauthLoginFlowService;
    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;

    @Autowired
    public CustomOidcUserService(OAuthLoginFlowService oauthLoginFlowService) {
        this(oauthLoginFlowService, new OidcUserService());
    }

    CustomOidcUserService(OAuthLoginFlowService oauthLoginFlowService,
                          OAuth2UserService<OidcUserRequest, OidcUser> delegate) {
        this.oauthLoginFlowService = oauthLoginFlowService;
        this.delegate = delegate;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser upstreamUser = delegate.loadUser(request);
        OAuthClaims claims = toOAuthClaims(request, upstreamUser);
        PlatformPrincipal principal = oauthLoginFlowService.authenticate(claims);

        Map<String, Object> userInfoClaims = new HashMap<>(upstreamUser.getClaims());
        if (upstreamUser.getUserInfo() != null) {
            userInfoClaims.putAll(upstreamUser.getUserInfo().getClaims());
        }
        userInfoClaims.put("platformPrincipal", principal);
        userInfoClaims.put("providerLogin", principal.userId());

        var authorities = new LinkedHashSet<GrantedAuthority>(upstreamUser.getAuthorities());
        principal.platformRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .forEach(authorities::add);

        return new DefaultOidcUser(
                authorities,
                upstreamUser.getIdToken(),
                new OidcUserInfo(userInfoClaims),
                "providerLogin"
        );
    }

    static OAuthClaims toOAuthClaims(OidcUserRequest request, OidcUser oidcUser) {
        Map<String, Object> claims = new HashMap<>(oidcUser.getClaims());
        String subject = asString(claims.get("sub"));
        String email = asString(claims.get("email"));
        boolean emailVerified = Boolean.TRUE.equals(claims.get("email_verified"));
        String providerLogin = firstPresent(
                asString(claims.get("preferred_username")),
                asString(claims.get("name")),
                email,
                subject
        );
        if (claims.get("picture") != null && claims.get("avatar_url") == null) {
            claims.put("avatar_url", claims.get("picture"));
        }

        return new OAuthClaims(
                request.getClientRegistration().getRegistrationId(),
                subject,
                email,
                emailVerified,
                providerLogin,
                claims
        );
    }

    private static String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String asString(Object value) {
        return value instanceof String str ? str : null;
    }
}
