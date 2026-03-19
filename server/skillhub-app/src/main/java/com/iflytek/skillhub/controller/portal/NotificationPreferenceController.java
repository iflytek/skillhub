package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.domain.NotificationChannel;
import com.iflytek.skillhub.notification.service.NotificationPreferenceService;
import com.iflytek.skillhub.notification.service.NotificationPreferenceService.PreferenceView;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/notification-preferences", "/api/web/notification-preferences"})
public class NotificationPreferenceController extends BaseApiController {

    private final NotificationPreferenceService preferenceService;

    public NotificationPreferenceController(NotificationPreferenceService preferenceService,
                                            ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public ApiResponse<List<NotificationPreferenceResponse>> getPreferences(
            @RequestAttribute("userId") String userId) {
        List<NotificationPreferenceResponse> prefs = preferenceService.getPreferences(userId).stream()
                .map(this::toResponse)
                .toList();
        return ok("response.success.read", prefs);
    }

    @PutMapping
    public ApiResponse<List<NotificationPreferenceResponse>> updatePreferences(
            @RequestAttribute("userId") String userId,
            @RequestBody NotificationPreferenceUpdateRequest request) {
        for (NotificationPreferenceUpdateRequest.PreferenceItem item : request.preferences()) {
            NotificationCategory category = NotificationCategory.valueOf(item.category());
            NotificationChannel channel = NotificationChannel.valueOf(item.channel());
            preferenceService.updatePreference(userId, category, channel, item.enabled());
        }
        List<NotificationPreferenceResponse> prefs = preferenceService.getPreferences(userId).stream()
                .map(this::toResponse)
                .toList();
        return ok("response.success.updated", prefs);
    }

    private NotificationPreferenceResponse toResponse(PreferenceView pv) {
        return new NotificationPreferenceResponse(
                pv.category().name(),
                pv.channel().name(),
                pv.enabled()
        );
    }
}
