package com.iflytek.skillhub.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.metrics.SkillHubMetrics;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitInterceptorCoverageTest {

    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
    private final AnonymousDownloadIdentityService anonymousDownloadIdentityService = mock(AnonymousDownloadIdentityService.class);
    private final ApiResponseFactory apiResponseFactory = mock(ApiResponseFactory.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SkillHubMetrics metrics = mock(SkillHubMetrics.class);

    private final RateLimitInterceptor interceptor = new RateLimitInterceptor(
            rateLimiter, clientIpResolver, anonymousDownloadIdentityService,
            apiResponseFactory, objectMapper, metrics
    );

    @Test
    void preHandle_withNonHandlerMethod_returnsTrue() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        assertThat(interceptor.preHandle(request, response, "not-a-handler")).isTrue();
    }

    @Test
    void preHandle_downloadCategory_withAnonymousIdentity() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getAttribute("userId")).thenReturn(null);
        when(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE))
                .thenReturn(Map.of("namespace", "global", "slug", "demo"));

        AnonymousDownloadIdentityService.AnonymousDownloadIdentity identity =
                new AnonymousDownloadIdentityService.AnonymousDownloadIdentity("ip-hash", "cookie-hash");
        when(anonymousDownloadIdentityService.resolve(request, response)).thenReturn(identity);
        when(rateLimiter.tryAcquire("ratelimit:download:ip:ip-hash:ns:global:slug:demo:latest", 10, 60))
                .thenReturn(true);
        when(rateLimiter.tryAcquire("ratelimit:download:anon:cookie-hash:ns:global:slug:demo:latest", 10, 60))
                .thenReturn(true);

        HandlerMethod handlerMethod = mock(HandlerMethod.class);
        RateLimit rateLimit = mock(RateLimit.class);
        when(rateLimit.category()).thenReturn("download");
        when(rateLimit.authenticated()).thenReturn(10);
        when(rateLimit.anonymous()).thenReturn(10);
        when(rateLimit.windowSeconds()).thenReturn(60);
        when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);

        assertThat(interceptor.preHandle(request, response, handlerMethod)).isTrue();
    }

    @Test
    void preHandle_downloadCategory_withNullTemplateVariables() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getAttribute("userId")).thenReturn(null);
        when(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)).thenReturn(null);

        AnonymousDownloadIdentityService.AnonymousDownloadIdentity identity =
                new AnonymousDownloadIdentityService.AnonymousDownloadIdentity("ip-hash", "cookie-hash");
        when(anonymousDownloadIdentityService.resolve(request, response)).thenReturn(identity);
        when(rateLimiter.tryAcquire("ratelimit:download:ip:ip-hash", 10, 60)).thenReturn(true);
        when(rateLimiter.tryAcquire("ratelimit:download:anon:cookie-hash", 10, 60)).thenReturn(true);

        HandlerMethod handlerMethod = mock(HandlerMethod.class);
        RateLimit rateLimit = mock(RateLimit.class);
        when(rateLimit.category()).thenReturn("download");
        when(rateLimit.authenticated()).thenReturn(10);
        when(rateLimit.anonymous()).thenReturn(10);
        when(rateLimit.windowSeconds()).thenReturn(60);
        when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);

        assertThat(interceptor.preHandle(request, response, handlerMethod)).isTrue();
    }

    @Test
    void stringValue_withBlankString_returnsNull() {
        String result = (String) org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                interceptor, "stringValue", "   "
        );
        assertThat(result).isNull();
    }
}
