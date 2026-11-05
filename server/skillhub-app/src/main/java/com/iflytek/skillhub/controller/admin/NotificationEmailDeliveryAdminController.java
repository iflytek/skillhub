package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.service.notificationemail.NotificationEmailDeliveryQueryService;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController
@Validated
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequestMapping({"/api/v1/admin/notification-email-deliveries", "/api/web/admin/notification-email-deliveries"})
public class NotificationEmailDeliveryAdminController extends BaseApiController {
    private final NotificationEmailDeliveryQueryService service;
    public NotificationEmailDeliveryAdminController(
            NotificationEmailDeliveryQueryService service, ApiResponseFactory factory) {
        super(factory);
        this.service = service;
    }
    @GetMapping
    public ApiResponse<NotificationEmailDeliveryAdminPageResponse> list(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ok("response.success.read", service.list(status, page, size));
    }
    @PostMapping("/{id}/retry")
    public ApiResponse<Void> retry(@PathVariable long id, @RequestBody NotificationEmailRetryRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        service.retry(id, principal.userId(), request.confirmDuplicateRisk());
        return ok("response.success.updated", null);
    }
}
