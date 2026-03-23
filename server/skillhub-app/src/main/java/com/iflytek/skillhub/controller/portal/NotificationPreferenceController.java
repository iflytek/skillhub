package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.domain.NotificationChannel;
import com.iflytek.skillhub.notification.service.NotificationPreferenceService;
import com.iflytek.skillhub.notification.service.NotificationPreferenceService.PreferenceCommand;
import com.iflytek.skillhub.notification.service.NotificationPreferenceService.PreferenceView;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
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
        if (request == null || request.preferences() == null) {
            throw new DomainBadRequestException("error.notification.preference.request.invalid");
        }
        List<PreferenceCommand> commands = request.preferences().stream()
                .map(item -> new PreferenceCommand(
                        parseCategory(item.category()),
                        parseChannel(item.channel()),
                        item.enabled()
                ))
                .toList();
        preferenceService.updatePreferences(userId, commands);
        List<NotificationPreferenceResponse> prefs = preferenceService.getPreferences(userId).stream()
                .map(this::toResponse)
                .toList();
        return ok("response.success.updated", prefs);
    }

    private NotificationCategory parseCategory(String category) {
        try {
            return NotificationCategory.valueOf(category);
        } catch (Exception ex) {
            throw new DomainBadRequestException("error.notification.preference.category.invalid", category);
        }
    }

    private NotificationChannel parseChannel(String channel) {
        try {
            return NotificationChannel.valueOf(channel);
        } catch (Exception ex) {
            throw new DomainBadRequestException("error.notification.preference.channel.invalid", channel);
        }
    }

    private NotificationPreferenceResponse toResponse(PreferenceView pv) {
        return new NotificationPreferenceResponse(
                pv.category().name(),
                pv.channel().name(),
                pv.enabled()
        );
    }
}
