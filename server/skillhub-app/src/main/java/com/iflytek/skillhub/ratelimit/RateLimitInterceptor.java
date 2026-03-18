package com.iflytek.skillhub.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final AnonymousDownloadIdentityService anonymousDownloadIdentityService;
    private final ApiResponseFactory apiResponseFactory;
    private final ObjectMapper objectMapper;

    public RateLimitInterceptor(RateLimiter rateLimiter,
                                ClientIpResolver clientIpResolver,
                                AnonymousDownloadIdentityService anonymousDownloadIdentityService,
                                ApiResponseFactory apiResponseFactory,
                                ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
        this.anonymousDownloadIdentityService = anonymousDownloadIdentityService;
        this.apiResponseFactory = apiResponseFactory;
        this.objectMapper = objectMapper;
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

        // Determine if user is authenticated
        String userId = (String) request.getAttribute("userId");
        boolean isAuthenticated = userId != null;

        // Get limit based on authentication status
        int limit = isAuthenticated ? rateLimit.authenticated() : rateLimit.anonymous();

        boolean allowed = isAuthenticated
                ? rateLimiter.tryAcquire("ratelimit:" + rateLimit.category() + ":user:" + userId, limit, rateLimit.windowSeconds())
                : checkAnonymousLimit(request, response, rateLimit, limit);

        if (!allowed) {
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
                                        RateLimit rateLimit,
                                        int limit) {
        if (!"download".equals(rateLimit.category())) {
            return rateLimiter.tryAcquire(
                    "ratelimit:" + rateLimit.category() + ":ip:" + clientIpResolver.resolve(request),
                    limit,
                    rateLimit.windowSeconds()
            );
        }

        AnonymousDownloadIdentityService.AnonymousDownloadIdentity identity =
                anonymousDownloadIdentityService.resolve(request, response);
        boolean ipAllowed = rateLimiter.tryAcquire(
                "ratelimit:download:ip:" + identity.ipHash(),
                limit,
                rateLimit.windowSeconds()
        );
        if (!ipAllowed) {
            return false;
        }
        return rateLimiter.tryAcquire(
                "ratelimit:download:anon:" + identity.cookieHash(),
                limit,
                rateLimit.windowSeconds()
        );
    }
}
