package com.iflytek.skillhub.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

class CustomOAuth2UserServiceTest {

    @Test
    void loadUser_mapsPlatformPrincipalAndRolesToAttributes() {
        OAuthLoginFlowService oauthLoginFlowService = mock(OAuthLoginFlowService.class);
        CustomOAuth2UserService service = new CustomOAuth2UserService(oauthLoginFlowService);

        PlatformPrincipal principal = new PlatformPrincipal(
                "usr_1", "Alice", "alice@example.com", "https://example.test/a.png",
                "github", Set.of("USER", "ADMIN")
        );

        Map<String, Object> upstreamAttrs = new HashMap<>();
        upstreamAttrs.put("login", "alice");
        OAuth2User upstreamUser = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                upstreamAttrs,
                "login"
        );

        OAuth2UserRequest request = mock(OAuth2UserRequest.class);
        when(oauthLoginFlowService.loadLoginContext(request))
                .thenReturn(new OAuthLoginFlowService.AuthenticatedLoginContext(upstreamUser, principal));

        OAuth2User result = service.loadUser(request);

        assertThat((Object) result.getAttribute("platformPrincipal")).isEqualTo(principal);
        assertThat((Object) result.getAttribute("providerLogin")).isEqualTo("usr_1");
        List<String> authorities = result.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        assertThat(authorities).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
        assertThat(result.getName()).isEqualTo("usr_1");
    }

    @Test
    void loadUser_withEmptyPlatformRoles_preservesUpstreamAuthorities() {
        OAuthLoginFlowService oauthLoginFlowService = mock(OAuthLoginFlowService.class);
        CustomOAuth2UserService service = new CustomOAuth2UserService(oauthLoginFlowService);

        PlatformPrincipal principal = new PlatformPrincipal(
                "usr_1", "Alice", "alice@example.com", null,
                "gitlab", Set.of()
        );

        Map<String, Object> upstreamAttrs = new HashMap<>();
        upstreamAttrs.put("login", "alice");
        OAuth2User upstreamUser = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_OAUTH")),
                upstreamAttrs,
                "login"
        );

        OAuth2UserRequest request = mock(OAuth2UserRequest.class);
        when(oauthLoginFlowService.loadLoginContext(request))
                .thenReturn(new OAuthLoginFlowService.AuthenticatedLoginContext(upstreamUser, principal));

        OAuth2User result = service.loadUser(request);

        List<String> authorities = result.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        assertThat(authorities).containsExactly("ROLE_OAUTH");
    }
}
