package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.ratelimit.RateLimit;
import com.iflytek.skillhub.service.NotificationEmailContactService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1", "/api/web"})
public class NotificationEmailController extends BaseApiController {
    private final NotificationEmailContactService service;
    private final com.iflytek.skillhub.ratelimit.RateLimiter limiter;
    private final com.iflytek.skillhub.ratelimit.ClientIpResolver ips;
    public NotificationEmailController(NotificationEmailContactService service, ApiResponseFactory responses,
            com.iflytek.skillhub.ratelimit.RateLimiter limiter, com.iflytek.skillhub.ratelimit.ClientIpResolver ips) {
        super(responses);
        this.service = service;
        this.limiter = limiter;
        this.ips = ips;
    }
    public record CodeRequest(String locale) {}
    public record VerifyRequest(String code) {}
    @GetMapping("/notification-email-contact")
    public ApiResponse<NotificationEmailContactResponse> contact(@RequestAttribute("userId") String userId) {
        return ok("response.success.read", service.status(userId));
    }
    @PostMapping("/notification-email-contact/verification-code")
    @RateLimit(category = "notification-email-code", authenticated = 10, anonymous = 0, windowSeconds = 3600)
    public ApiResponse<NotificationEmailContactResponse> requestCode(@RequestAttribute("userId") String userId,
            @RequestBody CodeRequest request, jakarta.servlet.http.HttpServletRequest http) {
        if (!limiter.tryAcquire("ratelimit:notification-email-code:source:" + ips.resolve(http), 30, 3600))
            throw new com.iflytek.skillhub.auth.exception.AuthFlowException(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "error.rateLimit.exceeded");
        return ok("response.success.updated", service.requestCode(userId, request == null ? null : request.locale()));
    }
    @PostMapping("/notification-email-contact/verify")
    @RateLimit(category = "notification-email-verify", authenticated = 20, anonymous = 0, windowSeconds = 300)
    public ApiResponse<NotificationEmailContactResponse> verify(
            @RequestAttribute("userId") String userId, @RequestBody VerifyRequest request) {
        return ok("response.success.updated", service.verify(userId, request == null ? null : request.code()));
    }
    @GetMapping("/notification-email-preferences")
    public ApiResponse<List<NotificationEmailPreferenceResponse>> preferences(
            @RequestAttribute("userId") String userId) {
        return ok("response.success.read", service.preferences(userId));
    }
    @PutMapping("/notification-email-preferences")
    public ApiResponse<List<NotificationEmailPreferenceResponse>> preferences(
            @RequestAttribute("userId") String userId, @RequestBody NotificationEmailPreferenceUpdateRequest request) {
        return ok("response.success.updated", service.updatePreferences(userId, request));
    }
}
