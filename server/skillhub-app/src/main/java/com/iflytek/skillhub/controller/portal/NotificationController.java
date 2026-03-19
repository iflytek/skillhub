package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.notification.domain.Notification;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.service.NotificationService;
import com.iflytek.skillhub.notification.sse.SseEmitterManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping({"/api/v1/notifications", "/api/web/notifications"})
public class NotificationController extends BaseApiController {

    private final NotificationService notificationService;
    private final SseEmitterManager sseEmitterManager;

    public NotificationController(NotificationService notificationService,
                                  SseEmitterManager sseEmitterManager,
                                  ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.notificationService = notificationService;
        this.sseEmitterManager = sseEmitterManager;
    }

    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> list(
            @RequestAttribute("userId") String userId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        NotificationCategory cat = category != null ? NotificationCategory.valueOf(category) : null;
        Page<Notification> result = notificationService.list(
                userId, cat, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        Page<NotificationResponse> mapped = result.map(this::toResponse);
        return ok("response.success.read", PageResponse.from(mapped));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount(@RequestAttribute("userId") String userId) {
        long count = notificationService.getUnreadCount(userId);
        return ok("response.success.read", Map.of("count", count));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id,
                                      @RequestAttribute("userId") String userId) {
        notificationService.markRead(id, userId);
        return ok("response.success.updated", null);
    }

    @PutMapping("/read-all")
    public ApiResponse<Map<String, Integer>> markAllRead(@RequestAttribute("userId") String userId) {
        int updated = notificationService.markAllRead(userId);
        return ok("response.success.updated", Map.of("updated", updated));
    }

    @GetMapping("/sse")
    public SseEmitter sse(@RequestAttribute("userId") String userId) {
        return sseEmitterManager.register(userId);
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getCategory().name(),
                n.getEventType(),
                n.getTitle(),
                n.getBodyJson(),
                n.getEntityType(),
                n.getEntityId(),
                n.getStatus().name(),
                n.getCreatedAt() != null ? n.getCreatedAt().toString() : null,
                n.getReadAt() != null ? n.getReadAt().toString() : null
        );
    }
}
