package com.iflytek.skillhub.auth.config;

import com.iflytek.skillhub.auth.mock.MockAuthFilter;
import com.iflytek.skillhub.auth.oauth.CustomOAuth2UserService;
import com.iflytek.skillhub.auth.oauth.OAuth2LoginFailureHandler;
import com.iflytek.skillhub.auth.oauth.OAuth2LoginSuccessHandler;
import com.iflytek.skillhub.auth.oauth.SkillHubOAuth2AuthorizationRequestResolver;
import com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry;
import com.iflytek.skillhub.auth.token.ApiTokenAuthenticationFilter;
import com.iflytek.skillhub.auth.token.ApiTokenScopeFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock
    private CustomOAuth2UserService customOAuth2UserService;

    @Mock
    private SkillHubOAuth2AuthorizationRequestResolver authorizationRequestResolver;

    @Mock
    private OAuth2LoginSuccessHandler successHandler;

    @Mock
    private OAuth2LoginFailureHandler failureHandler;

    @Mock
    private ApiTokenAuthenticationFilter apiTokenAuthenticationFilter;

    @Mock
    private ApiTokenScopeFilter apiTokenScopeFilter;

    @Mock
    private AuthenticationEntryPoint apiAuthenticationEntryPoint;

    @Mock
    private AccessDeniedHandler apiAccessDeniedHandler;

    @Mock
    private ObjectProvider<MockAuthFilter> mockAuthFilterProvider;

    @Mock
    private RouteSecurityPolicyRegistry routeSecurityPolicyRegistry;

    @Test
    void passwordEncoder_hashesAndMatchesValues() {
        SecurityConfig config = newConfig("https://skillhub.example.com", "");

        PasswordEncoder encoder = config.passwordEncoder();
        String encoded = encoder.encode("secret");

        assertThat(encoded).isNotEqualTo("secret");
        assertThat(encoder.matches("secret", encoded)).isTrue();
    }

    @Test
    void corsConfigurationSource_includesLoopbackAliasesForLoopbackPublicBaseUrl() {
        SecurityConfig config = newConfig("http://127.0.0.1:3001", "");

        CorsConfiguration cors = corsFor(config.corsConfigurationSource(), "/api/test");

        assertThat(cors.getAllowedOrigins())
                .containsExactlyInAnyOrder("http://127.0.0.1:3001", "http://localhost:3001");
        assertThat(cors.getAllowedMethods())
                .containsExactly("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(cors.getExposedHeaders())
                .containsExactly("Location", "X-Request-Id");
        assertThat(cors.getAllowCredentials()).isTrue();
    }

    @Test
    void corsConfigurationSource_usesOriginWithoutPortWhenPublicBaseUrlHasNoPort() {
        SecurityConfig config = newConfig("https://skillhub.example.com/app", "");

        CorsConfiguration cors = corsFor(config.corsConfigurationSource(), "/api/test");

        assertThat(cors.getAllowedOrigins()).containsExactly("https://skillhub.example.com");
    }

    @Test
    void corsConfigurationSource_returnsEmptyOriginListForInvalidBaseUrl() {
        SecurityConfig config = newConfig("://bad", "");

        CorsConfiguration cors = corsFor(config.corsConfigurationSource(), "/api/test");

        assertThat(cors.getAllowedOrigins()).isEmpty();
    }

    @Test
    void corsConfigurationSource_onlyAppliesToApiRoutes() {
        SecurityConfig config = newConfig("https://skillhub.example.com", "");

        CorsConfiguration cors = corsFor(config.corsConfigurationSource(), "/web/test");

        assertThat(cors).isNull();
    }

    @Test
    void corsConfigurationSource_includesConfiguredMockLoginOrigin() {
        SecurityConfig config = newConfig("http://localhost:3000", "http://localhost:3001/mock-uass");

        CorsConfiguration cors = corsFor(config.corsConfigurationSource(), "/api/test");

        assertThat(cors.getAllowedOrigins())
                .containsExactlyInAnyOrder(
                        "http://localhost:3000",
                        "http://127.0.0.1:3000",
                        "http://localhost:3001",
                        "http://127.0.0.1:3001"
                );
    }

    private SecurityConfig newConfig(String publicBaseUrl, String uassMockLoginBaseUrl) {
        return new SecurityConfig(
                customOAuth2UserService,
                authorizationRequestResolver,
                successHandler,
                failureHandler,
                apiTokenAuthenticationFilter,
                apiTokenScopeFilter,
                apiAuthenticationEntryPoint,
                apiAccessDeniedHandler,
                mockAuthFilterProvider,
                routeSecurityPolicyRegistry,
                publicBaseUrl,
                uassMockLoginBaseUrl
        );
    }

    private static CorsConfiguration corsFor(CorsConfigurationSource source, String path) {
        return source.getCorsConfiguration(new MockHttpServletRequest("GET", path));
    }
}
