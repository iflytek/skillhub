package com.iflytek.skillhub.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.mock.MockAuthFilter;
import com.iflytek.skillhub.auth.oauth.CustomOAuth2UserService;
import com.iflytek.skillhub.auth.oauth.OAuth2LoginFailureHandler;
import com.iflytek.skillhub.auth.oauth.OAuth2LoginSuccessHandler;
import com.iflytek.skillhub.auth.oauth.SkillHubOAuth2AuthorizationRequestResolver;
import com.iflytek.skillhub.auth.policy.RoutePolicyProperties;
import com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry;
import com.iflytek.skillhub.auth.token.ApiTokenAuthenticationFilter;
import com.iflytek.skillhub.auth.token.ApiTokenScopeFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;

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
            .withBean(ClientRegistrationRepository.class, () -> mock(ClientRegistrationRepository.class))
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

    @Test
    void csrfIgnoreMatcher_executesLambdaBody_whenProcessingPostRequest() throws ServletException, IOException {
        runner.run(ctx -> {
            SecurityFilterChain chain = ctx.getBean(SecurityFilterChain.class);
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/health");
            request.addHeader("Authorization", "Bearer token");
            MockHttpServletResponse response = new MockHttpServletResponse();

            boolean csrfFilterFound = false;
            for (Filter filter : chain.getFilters()) {
                if (filter instanceof CsrfFilter) {
                    csrfFilterFound = true;
                    filter.doFilter(request, response, (req, res) -> {});
                    break;
                }
            }
            assertThat(csrfFilterFound).isTrue();
        });
    }

    @Test
    void filterChain_containsMockAuthFilter_whenAvailable() throws Exception {
        HttpSecurity http = mock(HttpSecurity.class, org.mockito.Mockito.RETURNS_SELF);
        DefaultSecurityFilterChain chain = mock(DefaultSecurityFilterChain.class);
        doReturn(chain).when(http).build();

        MockAuthFilter mockFilter = mock(MockAuthFilter.class);
        org.springframework.beans.factory.ObjectProvider<MockAuthFilter> mockAuthFilterProvider =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(mockAuthFilterProvider.getIfAvailable()).thenReturn(mockFilter);

        SecurityConfig config = new SecurityConfig(
                mock(CustomOAuth2UserService.class),
                mock(SkillHubOAuth2AuthorizationRequestResolver.class),
                mock(OAuth2LoginSuccessHandler.class),
                mock(OAuth2LoginFailureHandler.class),
                mock(ApiTokenAuthenticationFilter.class),
                mock(ApiTokenScopeFilter.class),
                mock(AuthenticationEntryPoint.class),
                mock(AccessDeniedHandler.class),
                mockAuthFilterProvider,
                new RouteSecurityPolicyRegistry(new RoutePolicyProperties()),
                "https://skillhub.example.com",
                ""
        );

        config.filterChain(http);

        verify(http).addFilterBefore(mockFilter, AnonymousAuthenticationFilter.class);
    }
}
