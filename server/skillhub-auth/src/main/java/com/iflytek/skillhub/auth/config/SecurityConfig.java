package com.iflytek.skillhub.auth.config;

import com.iflytek.skillhub.auth.oauth.CustomOAuth2UserService;
import com.iflytek.skillhub.auth.oauth.CustomOidcUserService;
import com.iflytek.skillhub.auth.oauth.DingTalkOAuth2UserService;
import com.iflytek.skillhub.auth.oauth.DingTalkTokenResponseClient;
import com.iflytek.skillhub.auth.oauth.OAuth2LoginFailureHandler;
import com.iflytek.skillhub.auth.oauth.OAuth2LoginSuccessHandler;
import com.iflytek.skillhub.auth.oauth.SkillHubOAuth2AuthorizationRequestResolver;
import com.iflytek.skillhub.auth.mock.MockAuthFilter;
import com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry;
import com.iflytek.skillhub.auth.token.ApiTokenAuthenticationFilter;
import com.iflytek.skillhub.auth.token.ApiTokenScopeFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.AuthenticationEntryPoint;
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
    private final CustomOidcUserService customOidcUserService;
    private final DingTalkOAuth2UserService dingTalkOAuth2UserService;
    private final DingTalkTokenResponseClient dingTalkTokenResponseClient;
    private final SkillHubOAuth2AuthorizationRequestResolver authorizationRequestResolver;
    private final OAuth2LoginSuccessHandler successHandler;
    private final OAuth2LoginFailureHandler failureHandler;
    private final ApiTokenAuthenticationFilter apiTokenAuthenticationFilter;
    private final ApiTokenScopeFilter apiTokenScopeFilter;
    private final AuthenticationEntryPoint apiAuthenticationEntryPoint;
    private final AccessDeniedHandler apiAccessDeniedHandler;
    private final ObjectProvider<MockAuthFilter> mockAuthFilterProvider;
    private final RouteSecurityPolicyRegistry routeSecurityPolicyRegistry;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService,
                          CustomOidcUserService customOidcUserService,
                          DingTalkOAuth2UserService dingTalkOAuth2UserService,
                          DingTalkTokenResponseClient dingTalkTokenResponseClient,
                          SkillHubOAuth2AuthorizationRequestResolver authorizationRequestResolver,
                          OAuth2LoginSuccessHandler successHandler,
                          OAuth2LoginFailureHandler failureHandler,
                          ApiTokenAuthenticationFilter apiTokenAuthenticationFilter,
                          ApiTokenScopeFilter apiTokenScopeFilter,
                          AuthenticationEntryPoint apiAuthenticationEntryPoint,
                          AccessDeniedHandler apiAccessDeniedHandler,
                          ObjectProvider<MockAuthFilter> mockAuthFilterProvider,
                          RouteSecurityPolicyRegistry routeSecurityPolicyRegistry) {
        this.customOAuth2UserService = customOAuth2UserService;
        this.customOidcUserService = customOidcUserService;
        this.dingTalkOAuth2UserService = dingTalkOAuth2UserService;
        this.dingTalkTokenResponseClient = dingTalkTokenResponseClient;
        this.authorizationRequestResolver = authorizationRequestResolver;
        this.successHandler = successHandler;
        this.failureHandler = failureHandler;
        this.apiTokenAuthenticationFilter = apiTokenAuthenticationFilter;
        this.apiTokenScopeFilter = apiTokenScopeFilter;
        this.apiAuthenticationEntryPoint = apiAuthenticationEntryPoint;
        this.apiAccessDeniedHandler = apiAccessDeniedHandler;
        this.mockAuthFilterProvider = mockAuthFilterProvider;
        this.routeSecurityPolicyRegistry = routeSecurityPolicyRegistry;
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
            return routeSecurityPolicyRegistry.shouldIgnoreCsrf(request.getMethod(), path, authorization, hasSessionCookie(request));
        };

        http
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
                .tokenEndpoint(token -> token.accessTokenResponseClient(
                    new DelegatingAccessTokenResponseClient(dingTalkTokenResponseClient, new DefaultAuthorizationCodeTokenResponseClient())))
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(new DelegatingOAuth2UserService(customOAuth2UserService, dingTalkOAuth2UserService))
                    .oidcUserService(customOidcUserService))
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
                .invalidSessionStrategy((request, response) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("{\"code\":401,\"msg\":\"Session expired\"}");
                })
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

    private void configureRoutePolicies(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        for (RouteSecurityPolicyRegistry.RouteAuthorizationPolicy policy : routeSecurityPolicyRegistry.authorizationPolicies()) {
            switch (policy.accessLevel()) {
                case PERMIT_ALL -> auth.requestMatchers(policy.toRequestMatcher()).permitAll();
                case AUTHENTICATED -> auth.requestMatchers(policy.toRequestMatcher()).authenticated();
                case ROLE_PROTECTED -> auth.requestMatchers(policy.toRequestMatcher()).hasAnyRole(policy.roles());
            }
        }
    }

static boolean hasSessionCookie(HttpServletRequest request) {
        if (request.getRequestedSessionId() != null) {
            return true;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if ("SESSION".equals(cookie.getName()) || "JSESSIONID".equals(cookie.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Delegates OAuth2 user info loading to the appropriate service based on
     * the registrationId. DingTalk uses a custom service due to its non-standard
     * user info endpoint; all other providers use the standard service.
     */
    private static class DelegatingOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
        private final CustomOAuth2UserService defaultService;
        private final DingTalkOAuth2UserService dingTalkService;

        DelegatingOAuth2UserService(CustomOAuth2UserService defaultService, DingTalkOAuth2UserService dingTalkService) {
            this.defaultService = defaultService;
            this.dingTalkService = dingTalkService;
        }

        @Override
        public OAuth2User loadUser(OAuth2UserRequest userRequest) {
            if ("dingtalk".equals(userRequest.getClientRegistration().getRegistrationId())) {
                return dingTalkService.loadUser(userRequest);
            }
            return defaultService.loadUser(userRequest);
        }
    }

    /**
     * Delegates token exchange to the appropriate client based on the
     * registrationId. DingTalk requires a JSON body instead of form-urlencoded;
     * all other providers use the standard client.
     */
    private static class DelegatingAccessTokenResponseClient implements OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {
        private final DingTalkTokenResponseClient dingTalkClient;
        private final DefaultAuthorizationCodeTokenResponseClient defaultClient;

        DelegatingAccessTokenResponseClient(DingTalkTokenResponseClient dingTalkClient, DefaultAuthorizationCodeTokenResponseClient defaultClient) {
            this.dingTalkClient = dingTalkClient;
            this.defaultClient = defaultClient;
        }

        @Override
        public OAuth2AccessTokenResponse getTokenResponse(OAuth2AuthorizationCodeGrantRequest authorizationCodeGrantRequest) {
            if ("dingtalk".equals(authorizationCodeGrantRequest.getClientRegistration().getRegistrationId())) {
                return dingTalkClient.getTokenResponse(authorizationCodeGrantRequest);
            }
            return defaultClient.getTokenResponse(authorizationCodeGrantRequest);
        }
    }
}
