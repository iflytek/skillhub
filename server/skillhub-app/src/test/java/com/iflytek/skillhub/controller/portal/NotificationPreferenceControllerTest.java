package com.iflytek.skillhub.controller.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.NotificationPreferenceResponse;
import com.iflytek.skillhub.dto.NotificationPreferenceUpdateRequest;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.domain.NotificationChannel;
import com.iflytek.skillhub.notification.service.NotificationPreferenceService;
import com.iflytek.skillhub.notification.service.NotificationPreferenceService.PreferenceView;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticMessageSource;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceControllerTest {

    @Mock
    private NotificationPreferenceService preferenceService;

    private NotificationPreferenceController controller;

    @BeforeEach
    void setUp() {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("response.success.read", java.util.Locale.getDefault(), "ok");
        messageSource.addMessage("response.success.updated", java.util.Locale.getDefault(), "ok");
        ApiResponseFactory responseFactory = new ApiResponseFactory(
                messageSource,
                Clock.fixed(Instant.parse("2026-03-23T00:00:00Z"), ZoneOffset.UTC)
        );
        controller = new NotificationPreferenceController(preferenceService, responseFactory);
    }

    @Test
    void updatePreferences_shouldRejectNullRequest() {
        assertThrows(DomainBadRequestException.class, () -> controller.updatePreferences("user-1", null));
    }

    @Test
    void updatePreferences_shouldRejectInvalidCategory() {
        NotificationPreferenceUpdateRequest request = new NotificationPreferenceUpdateRequest(
                List.of(new NotificationPreferenceUpdateRequest.PreferenceItem("BAD", "IN_APP", true))
        );

        assertThrows(DomainBadRequestException.class, () -> controller.updatePreferences("user-1", request));
    }

    @Test
    void updatePreferences_shouldRejectInvalidChannel() {
        NotificationPreferenceUpdateRequest request = new NotificationPreferenceUpdateRequest(
                List.of(new NotificationPreferenceUpdateRequest.PreferenceItem("REVIEW", "BAD", true))
        );

        assertThrows(DomainBadRequestException.class, () -> controller.updatePreferences("user-1", request));
    }

    @Test
    void updatePreferences_shouldDelegateValidPayload() {
        NotificationPreferenceUpdateRequest request = new NotificationPreferenceUpdateRequest(
                List.of(new NotificationPreferenceUpdateRequest.PreferenceItem("REVIEW", "IN_APP", false))
        );
        when(preferenceService.getPreferences("user-1"))
                .thenReturn(List.of(new PreferenceView(NotificationCategory.REVIEW, NotificationChannel.IN_APP, false)));

        List<NotificationPreferenceResponse> response = controller.updatePreferences("user-1", request).data();

        verify(preferenceService).updatePreferences(eq("user-1"), anyList());
        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.category()).isEqualTo("REVIEW");
            assertThat(item.channel()).isEqualTo("IN_APP");
            assertThat(item.enabled()).isFalse();
        });
    }
}
