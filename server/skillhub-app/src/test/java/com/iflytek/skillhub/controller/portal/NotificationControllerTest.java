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
