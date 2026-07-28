package com.iflytek.skillhub.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.token.ApiTokenScopeFilter;
import com.iflytek.skillhub.auth.token.ApiTokenScopeService;
import com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class ApiAccessDeniedHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private ApiAccessDeniedHandler handler;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        ApiResponseFactory responseFactory = new ApiResponseFactory(
                messageSource,
                Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC)
        );
        handler = new ApiAccessDeniedHandler(
                objectMapper,
                responseFactory,
                new SensitiveLogSanitizer()
        );
        MDC.put("requestId", "req-610");
        LocaleContextHolder.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        LocaleContextHolder.resetLocaleContext();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldExposeLocalizedApiTokenScopeReasonAndRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/publish");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ApiTokenScopeService scopeService =
                new ApiTokenScopeService(objectMapper, new RouteSecurityPolicyRegistry());
        ApiTokenScopeFilter filter = new ApiTokenScopeFilter(scopeService, handler);
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1",
                "Alice",
                "alice@example.com",
                "",
                "api_token",
                Set.of("USER")
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("SCOPE_skill:read"))
                )
        );
        FilterChain chain = (servletRequest, servletResponse) -> {
            throw new AssertionError("Denied request must not continue");
        };

        filter.doFilter(request, response, chain);

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(body.path("msg").asText())
                .isEqualTo("API token is missing required scope: skill:publish");
        assertThat(body.path("requestId").asText()).isEqualTo("req-610");
    }

    @Test
    void shouldTranslateSafeApiTokenReason() throws Exception {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cli/v1/whoami");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ApiTokenScopeService scopeService =
                new ApiTokenScopeService(objectMapper, new RouteSecurityPolicyRegistry());
        ApiTokenScopeFilter filter = new ApiTokenScopeFilter(scopeService, handler);
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1",
                "Alice",
                "alice@example.com",
                "",
                "api_token",
                Set.of("USER")
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            throw new AssertionError("Denied request must not continue");
        });

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(body.path("msg").asText())
                .isEqualTo("API 令牌无法访问接口：/api/cli/v1/whoami");
    }

    @Test
    void shouldHideGenericAccessDeniedExceptionMessage() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("internal authorization detail"));

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(body.path("msg").asText()).isEqualTo("Forbidden");
        assertThat(response.getContentAsString()).doesNotContain("internal authorization detail");
    }
}
