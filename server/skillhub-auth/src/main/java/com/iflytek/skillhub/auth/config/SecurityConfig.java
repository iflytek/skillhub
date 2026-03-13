package com.iflytek.skillhub.auth.config;

import com.iflytek.skillhub.auth.oauth.CustomOAuth2UserService;
import com.iflytek.skillhub.auth.oauth.OAuth2LoginFailureHandler;
import com.iflytek.skillhub.auth.oauth.OAuth2LoginSuccessHandler;
import com.iflytek.skillhub.auth.mock.MockAuthFilter;
import com.iflytek.skillhub.auth.token.ApiTokenAuthenticationFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2LoginSuccessHandler successHandler;
    private final OAuth2LoginFailureHandler failureHandler;
    private final ApiTokenAuthenticationFilter apiTokenAuthenticationFilter;
    private final AuthenticationEntryPoint apiAuthenticationEntryPoint;
    private final AccessDeniedHandler apiAccessDeniedHandler;
    private final ObjectProvider<MockAuthFilter> mockAuthFilterProvider;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService,
                          OAuth2LoginSuccessHandler successHandler,
                          OAuth2LoginFailureHandler failureHandler,
                          ApiTokenAuthenticationFilter apiTokenAuthenticationFilter,
                          AuthenticationEntryPoint apiAuthenticationEntryPoint,
                          AccessDeniedHandler apiAccessDeniedHandler,
                          ObjectProvider<MockAuthFilter> mockAuthFilterProvider) {
        this.customOAuth2UserService = customOAuth2UserService;
        this.successHandler = successHandler;
        this.failureHandler = failureHandler;
        this.apiTokenAuthenticationFilter = apiTokenAuthenticationFilter;
        this.apiAuthenticationEntryPoint = apiAuthenticationEntryPoint;
        this.apiAccessDeniedHandler = apiAccessDeniedHandler;
        this.mockAuthFilterProvider = mockAuthFilterProvider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        var csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfHandler)
                .ignoringRequestMatchers("/api/v1/cli/**", "/api/compat/**")
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/health",
                    "/api/v1/auth/providers",
                    "/api/v1/auth/me",
                    "/api/v1/auth/local/**",
                    "/api/v1/cli/auth/device/**",
                    "/api/v1/cli/check",
                    "/actuator/health",
                    "/actuator/prometheus",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/.well-known/**",
                    "/api/compat/v1/search",
                    "/api/compat/v1/resolve/**"
                ).permitAll()
                .requestMatchers(HttpMethod.GET,
                    "/api/v1/skills",
                    "/api/v1/skills/*/*",
                    "/api/v1/skills/*/*/versions",
                    "/api/v1/skills/*/*/versions/*",
                    "/api/v1/skills/*/*/versions/*/files",
                    "/api/v1/skills/*/*/versions/*/file",
                    "/api/v1/skills/*/*/resolve",
                    "/api/v1/skills/*/*/download",
                    "/api/v1/skills/*/*/versions/*/download",
                    "/api/v1/skills/*/*/tags",
                    "/api/v1/skills/*/*/tags/*/files",
                    "/api/v1/skills/*/*/tags/*/file",
                    "/api/v1/skills/*/*/tags/*/download"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/namespaces", "/api/v1/namespaces/*").permitAll()
                .requestMatchers("/api/v1/admin/**").hasAnyRole("SUPER_ADMIN", "SKILL_ADMIN", "USER_ADMIN", "AUDITOR")
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                .successHandler(successHandler)
                .failureHandler(failureHandler)
            )
            .headers(headers -> headers
                .contentTypeOptions(contentTypeOptions -> {})
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
            .addFilterBefore(apiTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

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
