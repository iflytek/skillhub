package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.service.notificationemail.NotificationEmailDeliveryQueryService;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController
@Validated
@RequestMapping({"/api/v1/notification-email-deliveries", "/api/web/notification-email-deliveries"})
public class NotificationEmailDeliveryController extends BaseApiController {
    private final NotificationEmailDeliveryQueryService service;
    public NotificationEmailDeliveryController(
            NotificationEmailDeliveryQueryService service, ApiResponseFactory factory) {
        super(factory);
        this.service = service;
    }
    @GetMapping("/{id}")
    public ApiResponse<NotificationEmailDeliveryDetailResponse> detail(@PathVariable long id,
            @RequestAttribute("userId") String userId,
            @RequestParam(required = false) @Pattern(regexp = "[0-9]{1,19}") String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ok("response.success.read", service.detail(id, userId, cursor, size));
    }
}
