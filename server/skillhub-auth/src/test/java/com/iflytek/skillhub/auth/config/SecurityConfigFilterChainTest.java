package com.iflytek.skillhub.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.iflytek.skillhub.auth.mock.MockAuthFilter;
import com.iflytek.skillhub.auth.oauth.CustomOAuth2UserService;
import com.iflytek.skillhub.auth.oauth.OAuth2LoginFailureHandler;
import com.iflytek.skillhub.auth.oauth.OAuth2LoginSuccessHandler;
import com.iflytek.skillhub.auth.oauth.SkillHubOAuth2AuthorizationRequestResolver;
import com.iflytek.skillhub.auth.policy.RoutePolicyProperties;
import com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry;
import com.iflytek.skillhub.auth.token.ApiTokenAuthenticationFilter;
import com.iflytek.skillhub.auth.token.ApiTokenScopeFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

class SecurityConfigFilterChainTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(CustomOAuth2UserService.class, () -> mock(CustomOAuth2UserService.class))
            .withBean(SkillHubOAuth2AuthorizationRequestResolver.class, () -> mock(SkillHubOAuth2AuthorizationRequestResolver.class))
            .withBean(OAuth2LoginSuccessHandler.class, () -> mock(OAuth2LoginSuccessHandler.class))
            .withBean(OAuth2LoginFailureHandler.class, () -> mock(OAuth2LoginFailureHandler.class))
            .withBean(ApiTokenAuthenticationFilter.class, () -> mock(ApiTokenAuthenticationFilter.class))
            .withBean(ApiTokenScopeFilter.class, () -> mock(ApiTokenScopeFilter.class))
            .withBean(AuthenticationEntryPoint.class, () -> mock(AuthenticationEntryPoint.class))
            .withBean(AccessDeniedHandler.class, () -> mock(AccessDeniedHandler.class))
            .withBean(RoutePolicyProperties.class)
            .withBean(RouteSecurityPolicyRegistry.class)
            .withUserConfiguration(SecurityConfig.class)
            .withPropertyValues(
                    "skillhub.public.base-url=http://localhost:3000",
                    "skillhub.auth.uass.mock-login-base-url="
            );

    @Test
    void filterChain_beanIsCreated() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(SecurityFilterChain.class);
            assertThat(ctx.getBean(SecurityFilterChain.class)).isNotNull();
        });
    }

    @Test
    void filterChain_beanIsCreatedWithMockAuthFilter() {
        runner.withBean(MockAuthFilter.class, () -> mock(MockAuthFilter.class))
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(SecurityFilterChain.class);
                    assertThat(ctx.getBean(SecurityFilterChain.class)).isNotNull();
                });
    }
}
