package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.notification.email.NotificationEmailPolicy;
import com.iflytek.skillhub.service.NotificationEmailSettingsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/system-config/notification/email")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class NotificationEmailSettingsController extends BaseApiController {
    private final NotificationEmailSettingsService settings;
    public NotificationEmailSettingsController(
            NotificationEmailSettingsService settings, ApiResponseFactory responses) {
        super(responses);
        this.settings = settings;
    }
    @GetMapping
    public ApiResponse<NotificationEmailSettingsResponse> current() {
        return ok("response.success.read", response(settings.current()));
    }
    @PutMapping
    public ApiResponse<NotificationEmailSettingsResponse> update(@AuthenticationPrincipal PlatformPrincipal principal,
            @RequestBody NotificationEmailSettingsUpdateRequest request) {
        return ok("response.success.updated",
                response(settings.update(request.enabled(), request.digestWindowMinutes(), request.publicBaseUrl(),
                        request.version(), principal.userId())));
    }
    private NotificationEmailSettingsResponse response(NotificationEmailPolicy policy) {
        return new NotificationEmailSettingsResponse(policy.enabled(), policy.digestWindowMinutes(),
                policy.publicBaseUrl(), policy.version(), settings.smtpConfigured());
    }
}
