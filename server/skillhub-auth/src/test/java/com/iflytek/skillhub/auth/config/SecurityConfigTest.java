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

import org.mockito.ArgumentCaptor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        assertThat(cors.getAllowedOriginPatterns())
                .containsExactlyInAnyOrder("http://localhost:[*]", "http://127.0.0.1:[*]");
        assertThat(cors.getAllowedMethods())
                .containsExactly("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(cors.getExposedHeaders())
                .containsExactly("Location", "X-Request-Id");
        assertThat(cors.getAllowCredentials()).isTrue();
        assertThat(cors.checkOrigin("http://127.0.0.1:13194")).isEqualTo("http://127.0.0.1:13194");
        assertThat(cors.checkOrigin("http://localhost:13086")).isEqualTo("http://localhost:13086");
    }

    @Test
    void corsConfigurationSource_usesOriginWithoutPortWhenPublicBaseUrlHasNoPort() {
        SecurityConfig config = newConfig("https://skillhub.example.com/app", "");

        CorsConfiguration cors = corsFor(config.corsConfigurationSource(), "/api/test");

        assertThat(cors.getAllowedOrigins()).containsExactly("https://skillhub.example.com");
        assertThat(cors.getAllowedOriginPatterns()).isEmpty();
    }

    @Test
    void corsConfigurationSource_returnsEmptyOriginListForInvalidBaseUrl() {
        SecurityConfig config = newConfig("://bad", "");

        CorsConfiguration cors = corsFor(config.corsConfigurationSource(), "/api/test");

        assertThat(cors.getAllowedOrigins()).isEmpty();
        assertThat(cors.getAllowedOriginPatterns()).isEmpty();
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
        assertThat(cors.getAllowedOriginPatterns())
                .containsExactlyInAnyOrder("http://localhost:[*]", "http://127.0.0.1:[*]");
    }

    @Test
    void corsConfigurationSource_handlesNullBaseUrlGracefully() {
        SecurityConfig config = newConfig(null, null);

        CorsConfiguration cors = corsFor(config.corsConfigurationSource(), "/api/test");

        assertThat(cors.getAllowedOrigins()).isEmpty();
        assertThat(cors.getAllowedOriginPatterns()).isEmpty();
    }

    @Test
    void isLoopbackHost_recognizesIpv6Loopback() throws Exception {
        java.lang.reflect.Method method = SecurityConfig.class.getDeclaredMethod("isLoopbackHost", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(null, "::1")).isEqualTo(true);
    }

    @Test
    void corsConfigurationSource_skipsUriWithoutHost() {
        SecurityConfig config = newConfig("mailto:test@example.com", "");

        CorsConfiguration cors = corsFor(config.corsConfigurationSource(), "/api/test");

        assertThat(cors.getAllowedOrigins()).isEmpty();
        assertThat(cors.getAllowedOriginPatterns()).isEmpty();
    }

    @Test
    void corsConfigurationSource_skipsUriWithNullScheme() {
        SecurityConfig config = newConfig("//example.com", "");

        CorsConfiguration cors = corsFor(config.corsConfigurationSource(), "/api/test");

        assertThat(cors.getAllowedOrigins()).isEmpty();
        assertThat(cors.getAllowedOriginPatterns()).isEmpty();
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

    @Test
    void filterChain_csrfIgnoreMatcher_invokesRouteRegistryAndAddsMockAuthFilter() throws Exception {
        MockAuthFilter mockAuthFilter = mock(MockAuthFilter.class);
        when(mockAuthFilterProvider.getIfAvailable()).thenReturn(mockAuthFilter);
        when(routeSecurityPolicyRegistry.shouldIgnoreCsrf("/api/test", "Bearer token")).thenReturn(true);

        SecurityConfig config = newConfig("https://example.com", "");

        java.util.concurrent.atomic.AtomicReference<org.springframework.security.config.Customizer<org.springframework.security.config.annotation.web.configurers.CsrfConfigurer<HttpSecurity>>> csrfCustomizerRef =
                new java.util.concurrent.atomic.AtomicReference<>();

        HttpSecurity http = org.mockito.Mockito.mock(HttpSecurity.class, org.mockito.Mockito.RETURNS_SELF);
        org.mockito.Mockito.doAnswer(invocation -> {
            csrfCustomizerRef.set(invocation.getArgument(0));
            return http;
        }).when(http).csrf(any(org.springframework.security.config.Customizer.class));

        org.springframework.security.web.SecurityFilterChain mockChain =
                org.mockito.Mockito.mock(org.springframework.security.web.SecurityFilterChain.class);
        org.mockito.Mockito.doReturn(mockChain).when(http).build();

        config.filterChain(http);

        @SuppressWarnings("unchecked")
        org.springframework.security.config.annotation.web.configurers.CsrfConfigurer<HttpSecurity> csrfConfigurer =
                org.mockito.Mockito.mock(org.springframework.security.config.annotation.web.configurers.CsrfConfigurer.class, org.mockito.Mockito.RETURNS_SELF);
        csrfCustomizerRef.get().customize(csrfConfigurer);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<org.springframework.security.web.util.matcher.RequestMatcher> matcherCaptor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.security.web.util.matcher.RequestMatcher.class);
        org.mockito.Mockito.verify(csrfConfigurer).ignoringRequestMatchers(matcherCaptor.capture());
        org.springframework.security.web.util.matcher.RequestMatcher matcher = matcherCaptor.getValue();

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");
        request.addHeader("Authorization", "Bearer token");
        assertThat(matcher.matches(request)).isTrue();

        org.mockito.Mockito.verify(http).addFilterBefore(mockAuthFilter,
                org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class);
    }

    private static CorsConfiguration corsFor(CorsConfigurationSource source, String path) {
        return source.getCorsConfiguration(new MockHttpServletRequest("GET", path));
    }
}
