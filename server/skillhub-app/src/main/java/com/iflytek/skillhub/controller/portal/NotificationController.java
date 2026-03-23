package com.iflytek.skillhub.controller.portal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.notification.domain.Notification;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.service.NotificationService;
import com.iflytek.skillhub.notification.sse.SseEmitterManager;
import java.util.Collections;
import java.util.Map;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@Validated
@RequestMapping({"/api/v1/notifications", "/api/web/notifications"})
public class NotificationController extends BaseApiController {

    private final NotificationService notificationService;
    private final SseEmitterManager sseEmitterManager;
    private final ObjectMapper objectMapper;

    public NotificationController(NotificationService notificationService,
                                  SseEmitterManager sseEmitterManager,
                                  ObjectMapper objectMapper,
                                  ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.notificationService = notificationService;
        this.sseEmitterManager = sseEmitterManager;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> list(
            @RequestAttribute("userId") String userId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        NotificationCategory cat = parseCategory(category);
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

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteRead(@PathVariable Long id,
                                        @RequestAttribute("userId") String userId) {
        notificationService.deleteRead(id, userId);
        return ok("response.success.deleted", null);
    }

    @GetMapping("/sse")
    public SseEmitter sse(@RequestAttribute("userId") String userId) {
        return sseEmitterManager.register(userId);
    }

    private NotificationResponse toResponse(Notification n) {
        NotificationTarget target = resolveTarget(n);
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
                n.getReadAt() != null ? n.getReadAt().toString() : null,
                target.targetType(),
                target.targetId(),
                target.targetRoute()
        );
    }

    private NotificationTarget resolveTarget(Notification notification) {
        String eventType = notification.getEventType();
        String entityType = notification.getEntityType();
        Long entityId = notification.getEntityId();
        Map<String, Object> body = parseBody(notification.getBodyJson());
        String namespace = body.get("namespace") instanceof String value ? value : null;
        String slug = body.get("slug") instanceof String value ? value : null;

        if ("REVIEW_SUBMITTED".equals(eventType) && entityId != null) {
            return new NotificationTarget("REVIEW", entityId, "/dashboard/reviews/" + entityId);
        }
        if ("PROMOTION_SUBMITTED".equals(eventType)) {
            return new NotificationTarget("PROMOTION", entityId, "/dashboard/promotions");
        }
        if ("REPORT_SUBMITTED".equals(eventType)) {
            return new NotificationTarget("REPORT", entityId, "/dashboard/reports");
        }
        if (namespace != null && slug != null && ("SKILL".equals(entityType) || notification.getCategory() == NotificationCategory.PUBLISH)) {
            return new NotificationTarget("SKILL", entityId, "/space/" + namespace + "/" + slug);
        }
        return new NotificationTarget(entityType, entityId, "/dashboard/notifications");
    }

    private Map<String, Object> parseBody(String bodyJson) {
        if (bodyJson == null || bodyJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(bodyJson, new TypeReference<>() {});
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    private NotificationCategory parseCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        try {
            return NotificationCategory.valueOf(category);
        } catch (IllegalArgumentException ex) {
            throw new DomainBadRequestException("error.notification.category.invalid", category);
        }
    }

    private record NotificationTarget(String targetType, Long targetId, String targetRoute) {}
}
