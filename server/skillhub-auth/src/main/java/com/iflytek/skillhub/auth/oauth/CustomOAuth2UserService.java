package com.iflytek.skillhub.auth.oauth;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import java.util.HashMap;
import java.util.LinkedHashSet;
import org.springframework.stereotype.Service;

/**
 * Spring Security OAuth user-service bridge that extracts provider claims,
 * evaluates access policy, and maps the result to a {@link PlatformPrincipal}.
 */
@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final OAuthLoginFlowService oauthLoginFlowService;

    public CustomOAuth2UserService(OAuthLoginFlowService oauthLoginFlowService) {
        this.oauthLoginFlowService = oauthLoginFlowService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuthLoginFlowService.AuthenticatedLoginContext context = oauthLoginFlowService.loadLoginContext(request);
        PlatformPrincipal principal = context.principal();
        var attrs = new HashMap<>(context.upstreamUser().getAttributes());
        attrs.put("platformPrincipal", principal);
        // Store providerLogin under a fixed key so DefaultOAuth2User can find it
        attrs.put("providerLogin", principal.userId());

        var authorities = new LinkedHashSet<GrantedAuthority>(context.upstreamUser().getAuthorities());
        principal.platformRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .forEach(authorities::add);

        return new DefaultOAuth2User(authorities, attrs, "providerLogin");
    }
}
