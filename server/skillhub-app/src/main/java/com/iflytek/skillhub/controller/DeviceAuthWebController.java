package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.MessageResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Browser-side endpoint that lets an authenticated user authorize a pending
 * device code and records the operation in the audit log.
 */
@RestController
@RequestMapping("/api/v1/device")
public class DeviceAuthWebController extends BaseApiController {

    private final DeviceAuthService deviceAuthService;
    private final AuditLogService auditLogService;

    public DeviceAuthWebController(ApiResponseFactory responseFactory,
                                   DeviceAuthService deviceAuthService,
                                   AuditLogService auditLogService) {
        super(responseFactory);
        this.deviceAuthService = deviceAuthService;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/authorize")
    public ApiResponse<MessageResponse> authorizeDevice(
        @RequestBody AuthorizeRequest request,
        @AuthenticationPrincipal PlatformPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        deviceAuthService.authorizeDeviceCode(request.userCode(), principal.userId());
        auditLogService.record(
            principal.userId(),
            "DEVICE_AUTHORIZE",
            "DEVICE_CODE",
            null,
            MDC.get("requestId"),
            httpRequest.getRemoteAddr(),
            httpRequest.getHeader("User-Agent"),
            "{\"userCode\":\"" + request.userCode() + "\"}"
        );
        return ok("response.success.updated", new MessageResponse("Device authorized successfully"));
    }

    public record AuthorizeRequest(String userCode) {}
}
