package com.iflytek.skillhub.auth.config;

import com.iflytek.skillhub.auth.oauth.CustomOAuth2UserService;
import com.iflytek.skillhub.auth.oauth.OAuth2LoginFailureHandler;
import com.iflytek.skillhub.auth.oauth.OAuth2LoginSuccessHandler;
import com.iflytek.skillhub.auth.oauth.SkillHubOAuth2AuthorizationRequestResolver;
import com.iflytek.skillhub.auth.mock.MockAuthFilter;
import com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry;
import com.iflytek.skillhub.auth.token.ApiTokenAuthenticationFilter;
import com.iflytek.skillhub.auth.token.ApiTokenScopeFilter;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Central Spring Security configuration for browser sessions, API tokens, and
 * public versus protected endpoints.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self' 'unsafe-inline' 'unsafe-eval'",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "img-src 'self' data: blob: https:",
            "font-src 'self' data: https://fonts.gstatic.com",
            "connect-src 'self' ws: wss: http://localhost:* https://localhost:*",
            "object-src 'none'",
            "base-uri 'self'",
            "frame-ancestors 'none'",
            "form-action 'self'");

    private final CustomOAuth2UserService customOAuth2UserService;
    private final SkillHubOAuth2AuthorizationRequestResolver authorizationRequestResolver;
    private final OAuth2LoginSuccessHandler successHandler;
    private final OAuth2LoginFailureHandler failureHandler;
    private final ApiTokenAuthenticationFilter apiTokenAuthenticationFilter;
    private final ApiTokenScopeFilter apiTokenScopeFilter;
    private final AuthenticationEntryPoint apiAuthenticationEntryPoint;
    private final AccessDeniedHandler apiAccessDeniedHandler;
    private final ObjectProvider<MockAuthFilter> mockAuthFilterProvider;
    private final RouteSecurityPolicyRegistry routeSecurityPolicyRegistry;
    private final String publicBaseUrl;
    private final String uassMockLoginBaseUrl;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService,
                          SkillHubOAuth2AuthorizationRequestResolver authorizationRequestResolver,
                          OAuth2LoginSuccessHandler successHandler,
                          OAuth2LoginFailureHandler failureHandler,
                          ApiTokenAuthenticationFilter apiTokenAuthenticationFilter,
                          ApiTokenScopeFilter apiTokenScopeFilter,
                          AuthenticationEntryPoint apiAuthenticationEntryPoint,
                          AccessDeniedHandler apiAccessDeniedHandler,
                          ObjectProvider<MockAuthFilter> mockAuthFilterProvider,
                          RouteSecurityPolicyRegistry routeSecurityPolicyRegistry,
                          @Value("${skillhub.public.base-url:}") String publicBaseUrl,
                          @Value("${skillhub.auth.uass.mock-login-base-url:}") String uassMockLoginBaseUrl) {
        this.customOAuth2UserService = customOAuth2UserService;
        this.authorizationRequestResolver = authorizationRequestResolver;
        this.successHandler = successHandler;
        this.failureHandler = failureHandler;
        this.apiTokenAuthenticationFilter = apiTokenAuthenticationFilter;
        this.apiTokenScopeFilter = apiTokenScopeFilter;
        this.apiAuthenticationEntryPoint = apiAuthenticationEntryPoint;
        this.apiAccessDeniedHandler = apiAccessDeniedHandler;
        this.mockAuthFilterProvider = mockAuthFilterProvider;
        this.routeSecurityPolicyRegistry = routeSecurityPolicyRegistry;
        this.publicBaseUrl = publicBaseUrl;
        this.uassMockLoginBaseUrl = uassMockLoginBaseUrl;
    }

    /**
     * Builds the ordered security filter chain used by both browser and API
     * clients.
     *
     * <p>The chain mixes session-based authentication, bearer token support,
     * CSRF rules for browser traffic, and method-level authorization.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        var csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);
        RequestMatcher csrfIgnoreMatcher = request -> {
            String path = request.getRequestURI();
            String authorization = request.getHeader("Authorization");
            return routeSecurityPolicyRegistry.shouldIgnoreCsrf(path, authorization);
        };

        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfHandler)
                .ignoringRequestMatchers(csrfIgnoreMatcher)
            )
            .authorizeHttpRequests(auth -> {
                configureRoutePolicies(auth);
                auth.anyRequest().authenticated();
            })
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(authorizationRequestResolver))
                .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                .successHandler(successHandler)
                .failureHandler(failureHandler)
            )
            .headers(headers -> headers
                .contentTypeOptions(contentTypeOptions -> {})
                .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                .frameOptions(frameOptions -> frameOptions.deny())
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )
            .exceptionHandling(exceptions -> exceptions
                .accessDeniedHandler(apiAccessDeniedHandler)
                .defaultAuthenticationEntryPointFor(
                    apiAuthenticationEntryPoint,
                    new AntPathRequestMatcher("/api/**")
                )
            )
            .logout(logout -> logout
                .logoutUrl("/api/v1/auth/logout")
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .deleteCookies("SESSION")
            )
            .addFilterBefore(apiTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(apiTokenScopeFilter, ApiTokenAuthenticationFilter.class);

        MockAuthFilter mockAuthFilter = mockAuthFilterProvider.getIfAvailable();
        if (mockAuthFilter != null) {
            http.addFilterBefore(mockAuthFilter, AnonymousAuthenticationFilter.class);
        }

        return http.build();
    }

    /**
     * Provides the password encoder shared by local credentials and bootstrap
     * flows.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowCredentials(true);
        configuration.setAllowedOrigins(resolveAllowedOrigins(publicBaseUrl, uassMockLoginBaseUrl));
        configuration.setAllowedOriginPatterns(resolveAllowedOriginPatterns(publicBaseUrl, uassMockLoginBaseUrl));
        configuration.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()
        ));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Location", "X-Request-Id"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private void configureRoutePolicies(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        for (RouteSecurityPolicyRegistry.RouteAuthorizationPolicy policy : routeSecurityPolicyRegistry.authorizationPolicies()) {
            switch (policy.accessLevel()) {
                case PERMIT_ALL -> auth.requestMatchers(policy.toRequestMatcher()).permitAll();
                case AUTHENTICATED -> auth.requestMatchers(policy.toRequestMatcher()).authenticated();
                case ROLE_PROTECTED -> auth.requestMatchers(policy.toRequestMatcher()).hasAnyRole(policy.roles());
            }
        }
    }

    private static List<String> resolveAllowedOrigins(String... baseUrls) {
        Set<String> origins = new LinkedHashSet<>();
        for (String baseUrl : baseUrls) {
            String normalized = baseUrl == null ? "" : baseUrl.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            try {
                URI publicUri = URI.create(normalized);
                String scheme = publicUri.getScheme();
                String host = publicUri.getHost();
                int port = publicUri.getPort();
                if (scheme != null && host != null) {
                    origins.add(originOf(scheme, host, port));
                    if (isLoopbackHost(host)) {
                        origins.add(originOf(scheme, "localhost", port));
                        origins.add(originOf(scheme, "127.0.0.1", port));
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // Leave the allow-list empty if the public base URL is invalid.
            }
        }
        return List.copyOf(origins);
    }

    private static List<String> resolveAllowedOriginPatterns(String... baseUrls) {
        Set<String> originPatterns = new LinkedHashSet<>();
        for (String baseUrl : baseUrls) {
            String normalized = baseUrl == null ? "" : baseUrl.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            try {
                URI publicUri = URI.create(normalized);
                String scheme = publicUri.getScheme();
                String host = publicUri.getHost();
                if (scheme != null && host != null && isLoopbackHost(host)) {
                    originPatterns.add(originPatternOf(scheme, "localhost"));
                    originPatterns.add(originPatternOf(scheme, "127.0.0.1"));
                }
            } catch (IllegalArgumentException ignored) {
                // Leave the pattern allow-list empty if the public base URL is invalid.
            }
        }
        return List.copyOf(originPatterns);
    }

    private static boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }

    private static String originOf(String scheme, String host, int port) {
        if (port < 0) {
            return scheme + "://" + host;
        }
        return scheme + "://" + host + ":" + port;
    }

    private static String originPatternOf(String scheme, String host) {
        return scheme + "://" + host + ":[*]";
    }
}
