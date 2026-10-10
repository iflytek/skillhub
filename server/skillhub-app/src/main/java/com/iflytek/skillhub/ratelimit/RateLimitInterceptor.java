package com.iflytek.skillhub.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.RateLimitProperties;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.metrics.SkillHubMetrics;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * Enforces the {@link RateLimit} annotation by resolving caller identity and delegating quota
 * checks to the configured rate limiter implementation.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final AnonymousDownloadIdentityService anonymousDownloadIdentityService;
    private final ApiResponseFactory apiResponseFactory;
    private final ObjectMapper objectMapper;
    private final SkillHubMetrics metrics;
    private final RateLimitProperties properties;

    public RateLimitInterceptor(RateLimiter rateLimiter,
                                ClientIpResolver clientIpResolver,
                                AnonymousDownloadIdentityService anonymousDownloadIdentityService,
                                ApiResponseFactory apiResponseFactory,
                                ObjectMapper objectMapper,
                                SkillHubMetrics metrics,
                                RateLimitProperties properties) {
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
        this.anonymousDownloadIdentityService = anonymousDownloadIdentityService;
        this.apiResponseFactory = apiResponseFactory;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);

        if (rateLimit == null) {
            return true;
        }

        // Master switch: when disabled, perform no quota checks at all.
        if (!properties.isEnabled()) {
            return true;
        }

        // Determine if user is authenticated
        String userId = (String) request.getAttribute("userId");
        boolean isAuthenticated = userId != null;

        // Effective limits: runtime config overrides (per category) fall back to the
        // annotation defaults, so unconfigured deployments behave exactly as before.
        String category = rateLimit.category();
        int windowSeconds = properties.windowSecondsFor(category, rateLimit.windowSeconds());
        int limit = isAuthenticated
                ? properties.authenticatedFor(category, rateLimit.authenticated())
                : properties.anonymousFor(category, rateLimit.anonymous());
        String resourceSuffix = resolveResourceSuffix(category, request);

        boolean allowed = isAuthenticated
                ? rateLimiter.tryAcquire(
                        "ratelimit:" + category + ":user:" + userId + resourceSuffix,
                        limit,
                        windowSeconds)
                : checkAnonymousLimit(request, response, category, limit, windowSeconds, resourceSuffix);

        if (!allowed) {
            metrics.incrementRateLimitExceeded(category);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiResponse<Void> body = apiResponseFactory.error(429, "error.rateLimit.exceeded");
            objectMapper.writeValue(response.getOutputStream(), body);
            return false;
        }

        return true;
    }

    private boolean checkAnonymousLimit(HttpServletRequest request,
                                        HttpServletResponse response,
                                        String category,
                                        int limit,
                                        int windowSeconds,
                                        String resourceSuffix) {
        if (!"download".equals(category)) {
            return rateLimiter.tryAcquire(
                    "ratelimit:" + category + ":ip:" + clientIpResolver.resolve(request) + resourceSuffix,
                    limit,
                    windowSeconds
            );
        }

        AnonymousDownloadIdentityService.AnonymousDownloadIdentity identity =
                anonymousDownloadIdentityService.resolve(request, response);
        boolean ipAllowed = rateLimiter.tryAcquire(
                "ratelimit:download:ip:" + identity.ipHash() + resourceSuffix,
                limit,
                windowSeconds
        );
        if (!ipAllowed) {
            return false;
        }
        return rateLimiter.tryAcquire(
                "ratelimit:download:anon:" + identity.cookieHash() + resourceSuffix,
                limit,
                windowSeconds
        );
    }

    @SuppressWarnings("unchecked")
    private String resolveResourceSuffix(String category, HttpServletRequest request) {
        if (!"download".equals(category)) {
            return "";
        }
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(attribute instanceof Map<?, ?> templateVariables)) {
            return "";
        }
        String namespace = stringValue(templateVariables.get("namespace"));
        String slug = stringValue(templateVariables.get("slug"));
        String version = stringValue(templateVariables.get("version"));
        String tagName = stringValue(templateVariables.get("tagName"));
        if (namespace == null || slug == null) {
            return "";
        }
        String target = version != null ? "version:" + version : tagName != null ? "tag:" + tagName : "latest";
        return ":ns:" + namespace + ":slug:" + slug + ":" + target;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }
}
