package com.iflytek.skillhub.controller.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.NotificationResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.notification.domain.Notification;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.service.NotificationService;
import com.iflytek.skillhub.notification.sse.SseEmitterManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private SseEmitterManager sseEmitterManager;

    private NotificationController controller;

    @BeforeEach
    void setUp() {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("response.success.read", java.util.Locale.getDefault(), "ok");
        ApiResponseFactory responseFactory = new ApiResponseFactory(
                messageSource,
                Clock.fixed(Instant.parse("2026-03-20T00:00:00Z"), ZoneOffset.UTC)
        );
        controller = new NotificationController(notificationService, sseEmitterManager, new ObjectMapper(), responseFactory);
    }

    @Test
    void list_shouldExposeReviewTargetRouteForSubmittedReviewNotifications() {
        Notification notification = notification(
                11L,
                NotificationCategory.REVIEW,
                "REVIEW_SUBMITTED",
                "{\"namespace\":\"demo\",\"slug\":\"skill-a\"}",
                "REVIEW",
                99L
        );
        when(notificationService.list(org.mockito.ArgumentMatchers.eq("user-1"), org.mockito.ArgumentMatchers.eq(NotificationCategory.REVIEW), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(notification)));

        PageResponse<NotificationResponse> page = controller.list("user-1", "REVIEW", 0, 20).data();

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.targetType()).isEqualTo("REVIEW");
            assertThat(item.targetId()).isEqualTo(99L);
            assertThat(item.targetRoute()).isEqualTo("/dashboard/reviews/99");
        });
        verify(notificationService).list(org.mockito.ArgumentMatchers.eq("user-1"), org.mockito.ArgumentMatchers.eq(NotificationCategory.REVIEW), org.mockito.ArgumentMatchers.any(Pageable.class));
    }

    @Test
    void list_shouldExposeSkillRouteForResolvedWorkflowNotifications() {
        Notification notification = notification(
                12L,
                NotificationCategory.REVIEW,
                "REVIEW_APPROVED",
                "{\"namespace\":\"demo\",\"slug\":\"skill-a\"}",
                "SKILL",
                101L
        );
        when(notificationService.list(org.mockito.ArgumentMatchers.eq("user-1"), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(notification)));

        PageResponse<NotificationResponse> page = controller.list("user-1", null, 0, 20).data();

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.targetType()).isEqualTo("SKILL");
            assertThat(item.targetId()).isEqualTo(101L);
            assertThat(item.targetRoute()).isEqualTo("/space/demo/skill-a");
        });
    }

    @Test
    void deleteRead_shouldDelegateToService() {
        controller.deleteRead(10L, "user-1");

        verify(notificationService).deleteRead(10L, "user-1");
    }

    @Test
    void list_shouldExposePromotionInboxRouteForPromotionSubmittedNotifications() {
        Notification notification = notification(
                13L,
                NotificationCategory.PROMOTION,
                "PROMOTION_SUBMITTED",
                "{\"namespace\":\"demo\",\"slug\":\"skill-a\"}",
                "PROMOTION",
                33L
        );
        when(notificationService.list(org.mockito.ArgumentMatchers.eq("user-1"), org.mockito.ArgumentMatchers.eq(NotificationCategory.PROMOTION), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(notification)));

        PageResponse<NotificationResponse> page = controller.list("user-1", "PROMOTION", 0, 20).data();

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.targetType()).isEqualTo("PROMOTION");
            assertThat(item.targetId()).isEqualTo(33L);
            assertThat(item.targetRoute()).isEqualTo("/dashboard/promotions");
        });
    }

    @Test
    void list_shouldExposeReportInboxRouteForReportSubmittedNotifications() {
        Notification notification = notification(
                14L,
                NotificationCategory.REPORT,
                "REPORT_SUBMITTED",
                "{\"namespace\":\"demo\",\"slug\":\"skill-a\"}",
                "REPORT",
                44L
        );
        when(notificationService.list(org.mockito.ArgumentMatchers.eq("user-1"), org.mockito.ArgumentMatchers.eq(NotificationCategory.REPORT), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(notification)));

        PageResponse<NotificationResponse> page = controller.list("user-1", "REPORT", 0, 20).data();

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.targetType()).isEqualTo("REPORT");
            assertThat(item.targetId()).isEqualTo(44L);
            assertThat(item.targetRoute()).isEqualTo("/dashboard/reports");
        });
    }

    @Test
    void unreadCount_shouldDelegateToService() {
        when(notificationService.getUnreadCount("user-1")).thenReturn(5L);

        var response = controller.unreadCount("user-1");

        assertThat(response.data().get("count")).isEqualTo(5L);
    }

    @Test
    void markRead_shouldDelegateToService() {
        var response = controller.markRead(10L, "user-1");

        assertThat(response.code()).isEqualTo(0);
        verify(notificationService).markRead(10L, "user-1");
    }

    @Test
    void markAllRead_shouldDelegateToService() {
        when(notificationService.markAllRead("user-1")).thenReturn(3);

        var response = controller.markAllRead("user-1");

        assertThat(response.data().get("updated")).isEqualTo(3);
    }

    @Test
    void sse_shouldDelegateToManager() {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                new org.springframework.web.servlet.mvc.method.annotation.SseEmitter();
        when(sseEmitterManager.register("user-1")).thenReturn(emitter);

        var result = controller.sse("user-1");

        assertThat(result).isSameAs(emitter);
    }

    @Test
    void list_shouldThrowForInvalidCategory() {
        org.junit.jupiter.api.Assertions.assertThrows(
                com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class,
                () -> controller.list("user-1", "INVALID", 0, 20)
        );
    }

    @Test
    void list_shouldReturnDefaultRouteForUnknownEventType() {
        Notification notification = notification(
                15L,
                NotificationCategory.REVIEW,
                "UNKNOWN_EVENT",
                "{}",
                "OTHER",
                55L
        );
        when(notificationService.list(org.mockito.ArgumentMatchers.eq("user-1"), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(notification)));

        PageResponse<NotificationResponse> page = controller.list("user-1", null, 0, 20).data();

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.targetType()).isEqualTo("OTHER");
            assertThat(item.targetRoute()).isEqualTo("/dashboard/notifications");
        });
    }

    @Test
    void list_shouldHandleNullBodyJsonAndTimestamps() {
        Notification notification = new Notification(
                "user-1",
                NotificationCategory.PUBLISH,
                "SKILL_PUBLISHED",
                "Title",
                null,
                "SKILL",
                77L,
                null
        );
        ReflectionTestUtils.setField(notification, "id", 16L);
        ReflectionTestUtils.setField(notification, "readAt", null);
        when(notificationService.list(org.mockito.ArgumentMatchers.eq("user-1"), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(notification)));

        PageResponse<NotificationResponse> page = controller.list("user-1", null, 0, 20).data();

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.targetType()).isEqualTo("SKILL");
            assertThat(item.createdAt()).isNull();
            assertThat(item.readAt()).isNull();
        });
    }

    @Test
    void list_shouldExposeSkillRouteWhenNamespaceAndSlugPresentWithPublishCategory() {
        Notification notification = notification(
                17L,
                NotificationCategory.PUBLISH,
                "SKILL_PUBLISHED",
                "{\"namespace\":\"demo\",\"slug\":\"skill-b\"}",
                "SKILL",
                88L
        );
        when(notificationService.list(org.mockito.ArgumentMatchers.eq("user-1"), org.mockito.ArgumentMatchers.eq(NotificationCategory.PUBLISH), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(notification)));

        PageResponse<NotificationResponse> page = controller.list("user-1", "PUBLISH", 0, 20).data();

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.targetType()).isEqualTo("SKILL");
            assertThat(item.targetRoute()).isEqualTo("/space/demo/skill-b");
        });
    }

    @Test
    void list_shouldHandleInvalidBodyJsonGracefully() {
        Notification notification = notification(
                18L,
                NotificationCategory.REVIEW,
                "REVIEW_SUBMITTED",
                "not-valid-json",
                "REVIEW",
                99L
        );
        when(notificationService.list(org.mockito.ArgumentMatchers.eq("user-1"), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(notification)));

        PageResponse<NotificationResponse> page = controller.list("user-1", null, 0, 20).data();

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.targetType()).isEqualTo("REVIEW");
            assertThat(item.targetRoute()).isEqualTo("/dashboard/reviews/99");
        });
    }

    @Test
    void list_shouldHandleBlankCategory() {
        when(notificationService.list(org.mockito.ArgumentMatchers.eq("user-1"), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of()));

        PageResponse<NotificationResponse> page = controller.list("user-1", "   ", 0, 20).data();

        assertThat(page.items()).isEmpty();
    }

    @Test
    void list_shouldHandleBlankBodyJson() {
        Notification notification = new Notification(
                "user-1",
                NotificationCategory.PUBLISH,
                "SKILL_PUBLISHED",
                "Title",
                "   ",
                "SKILL",
                77L,
                Instant.parse("2026-03-20T00:00:00Z")
        );
        ReflectionTestUtils.setField(notification, "id", 19L);
        when(notificationService.list(org.mockito.ArgumentMatchers.eq("user-1"), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(notification)));

        PageResponse<NotificationResponse> page = controller.list("user-1", null, 0, 20).data();

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.targetType()).isEqualTo("SKILL");
            assertThat(item.targetRoute()).isEqualTo("/dashboard/notifications");
        });
    }

    @Test
    void list_shouldReturnDefaultRouteWhenReviewSubmittedWithNullEntityId() {
        Notification notification = notification(
                20L,
                NotificationCategory.REVIEW,
                "REVIEW_SUBMITTED",
                "{\"namespace\":\"demo\",\"slug\":\"skill-a\"}",
                "REVIEW",
                null
        );
        when(notificationService.list(org.mockito.ArgumentMatchers.eq("user-1"), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(notification)));

        PageResponse<NotificationResponse> page = controller.list("user-1", null, 0, 20).data();

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.targetType()).isEqualTo("REVIEW");
            assertThat(item.targetRoute()).isEqualTo("/dashboard/notifications");
        });
    }

    @Test
    void list_shouldReturnDefaultRouteWhenNamespaceMissing() {
        Notification notification = notification(
                21L,
                NotificationCategory.PUBLISH,
                "SKILL_PUBLISHED",
                "{\"namespace\":\"demo\"}",
                "SKILL",
                88L
        );
        when(notificationService.list(org.mockito.ArgumentMatchers.eq("user-1"), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(notification)));

        PageResponse<NotificationResponse> page = controller.list("user-1", null, 0, 20).data();

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.targetType()).isEqualTo("SKILL");
            assertThat(item.targetRoute()).isEqualTo("/dashboard/notifications");
        });
    }

    @Test
    void list_shouldReturnDefaultRouteWhenSlugMissing() {
        Notification notification = notification(
                22L,
                NotificationCategory.PUBLISH,
                "SKILL_PUBLISHED",
                "{\"slug\":\"skill-b\"}",
                "SKILL",
                88L
        );
        when(notificationService.list(org.mockito.ArgumentMatchers.eq("user-1"), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(notification)));

        PageResponse<NotificationResponse> page = controller.list("user-1", null, 0, 20).data();

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.targetType()).isEqualTo("SKILL");
            assertThat(item.targetRoute()).isEqualTo("/dashboard/notifications");
        });
    }

    @Test
    void list_shouldReturnDefaultRouteWhenNeitherSkillNorPublish() {
        Notification notification = notification(
                23L,
                NotificationCategory.REVIEW,
                "SOME_EVENT",
                "{\"namespace\":\"demo\",\"slug\":\"skill-c\"}",
                "OTHER",
                88L
        );
        when(notificationService.list(org.mockito.ArgumentMatchers.eq("user-1"), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(notification)));

        PageResponse<NotificationResponse> page = controller.list("user-1", null, 0, 20).data();

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.targetType()).isEqualTo("OTHER");
            assertThat(item.targetRoute()).isEqualTo("/dashboard/notifications");
        });
    }

    @Test
    void list_shouldHandleNullReadAt() {
        Notification notification = new Notification(
                "user-1",
                NotificationCategory.PUBLISH,
                "SKILL_PUBLISHED",
                "Title",
                null,
                "SKILL",
                77L,
                Instant.parse("2026-03-20T00:00:00Z")
        );
        ReflectionTestUtils.setField(notification, "id", 24L);
        ReflectionTestUtils.setField(notification, "readAt", null);
        when(notificationService.list(org.mockito.ArgumentMatchers.eq("user-1"), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(notification)));

        PageResponse<NotificationResponse> page = controller.list("user-1", null, 0, 20).data();

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.readAt()).isNull();
        });
    }

    private Notification notification(Long id,
                                      NotificationCategory category,
                                      String eventType,
                                      String bodyJson,
                                      String entityType,
                                      Long entityId) {
        Notification notification = new Notification(
                "user-1",
                category,
                eventType,
                "Title",
                bodyJson,
                entityType,
                entityId,
                Instant.parse("2026-03-20T00:00:00Z")
        );
        ReflectionTestUtils.setField(notification, "id", id);
        return notification;
    }
}
