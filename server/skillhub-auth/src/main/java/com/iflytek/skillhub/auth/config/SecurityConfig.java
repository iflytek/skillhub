package com.iflytek.skillhub.auth.config;

import com.iflytek.skillhub.auth.oauth.CustomOAuth2UserService;
import com.iflytek.skillhub.auth.oauth.OAuth2LoginFailureHandler;
import com.iflytek.skillhub.auth.oauth.OAuth2LoginSuccessHandler;
import com.iflytek.skillhub.auth.oauth.SkillHubOAuth2AuthorizationRequestResolver;
import com.iflytek.skillhub.auth.mock.MockAuthFilter;
import com.iflytek.skillhub.auth.token.ApiTokenAuthenticationFilter;
import com.iflytek.skillhub.auth.token.ApiTokenScopeFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
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

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService,
                          SkillHubOAuth2AuthorizationRequestResolver authorizationRequestResolver,
                          OAuth2LoginSuccessHandler successHandler,
                          OAuth2LoginFailureHandler failureHandler,
                          ApiTokenAuthenticationFilter apiTokenAuthenticationFilter,
                          ApiTokenScopeFilter apiTokenScopeFilter,
                          AuthenticationEntryPoint apiAuthenticationEntryPoint,
                          AccessDeniedHandler apiAccessDeniedHandler,
                          ObjectProvider<MockAuthFilter> mockAuthFilterProvider) {
        this.customOAuth2UserService = customOAuth2UserService;
        this.authorizationRequestResolver = authorizationRequestResolver;
        this.successHandler = successHandler;
        this.failureHandler = failureHandler;
        this.apiTokenAuthenticationFilter = apiTokenAuthenticationFilter;
        this.apiTokenScopeFilter = apiTokenScopeFilter;
        this.apiAuthenticationEntryPoint = apiAuthenticationEntryPoint;
        this.apiAccessDeniedHandler = apiAccessDeniedHandler;
        this.mockAuthFilterProvider = mockAuthFilterProvider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        var csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);
        RequestMatcher csrfIgnoreMatcher = request -> {
            String path = request.getRequestURI();
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                return true;
            }
            if (path == null) {
                return false;
            }
            return path.startsWith("/api/")
                    || path.equals("/api/v1/publish")
                    || path.startsWith("/api/v1/auth/device/");
        };

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfHandler)
                .ignoringRequestMatchers(csrfIgnoreMatcher)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/health",
                    "/api/v1/search",
                    "/api/v1/resolve/**",
                    "/api/v1/download/**",
                    "/api/v1/auth/providers",
                    "/api/v1/auth/methods",
                    "/api/v1/auth/me",
                    "/api/v1/auth/session/bootstrap",
                    "/api/v1/auth/direct/login",
                    "/api/v1/auth/local/**",
                    "/api/v1/auth/device/**",
                    "/api/v1/check",
                    "/actuator/health",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/.well-known/**",
                    "/api/v1/search",
                    "/api/v1/resolve/**",
                    "/api/v1/download/**"
                ).permitAll()
                .requestMatchers("/actuator/prometheus").hasAnyRole("SUPER_ADMIN", "AUDITOR")
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/skills/*/star",
                    "/api/v1/skills/*/rating",
                    "/api/web/skills/*/star",
                    "/api/web/skills/*/rating"
                ).authenticated()
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/skills",
                    "/api/v1/skills/*/*",
                    "/api/v1/skills/*/*/versions",
                    "/api/v1/skills/*/*/versions/*",
                    "/api/v1/skills/*/*/download",
                    "/api/v1/skills/*/*/versions/*/download",
                    "/api/v1/skills/*/*/versions/*/files",
                    "/api/v1/skills/*/*/versions/*/file",
                    "/api/v1/skills/*/*/resolve",
                    "/api/v1/skills/*/*/tags",
                    "/api/v1/skills/*/*/tags/*/download",
                    "/api/v1/skills/*/*/tags/*/files",
                    "/api/v1/skills/*/*/tags/*/file",
                    "/api/web/skills",
                    "/api/web/skills/*/*",
                    "/api/web/skills/*/*/versions",
                    "/api/web/skills/*/*/versions/*",
                    "/api/web/skills/*/*/download",
                    "/api/web/skills/*/*/versions/*/download",
                    "/api/web/skills/*/*/versions/*/files",
                    "/api/web/skills/*/*/versions/*/file",
                    "/api/web/skills/*/*/resolve",
                    "/api/web/skills/*/*/tags",
                    "/api/web/skills/*/*/tags/*/download",
                    "/api/web/skills/*/*/tags/*/files",
                    "/api/web/skills/*/*/tags/*/file"
                ).permitAll()
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/namespaces",
                    "/api/v1/namespaces/*",
                    "/api/web/namespaces",
                    "/api/web/namespaces/*"
                ).permitAll()
                .requestMatchers("/api/v1/admin/**").authenticated()
                .anyRequest().authenticated()
            )
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
