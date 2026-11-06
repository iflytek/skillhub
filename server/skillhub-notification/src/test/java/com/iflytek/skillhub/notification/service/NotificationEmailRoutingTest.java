package com.iflytek.skillhub.notification.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.iflytek.skillhub.notification.domain.*;
import com.iflytek.skillhub.notification.email.delivery.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
class NotificationEmailRoutingTest {
    @SuppressWarnings("unchecked")
    @Test
    void disabledInAppStillEnqueuesEmailAndFailedEmailDoesNotUndoSavedInApp() {
        var repository = mock(NotificationRepository.class);
        var preferences = mock(NotificationPreferenceService.class);
        var email = mock(NotificationEmailEnqueuer.class);
        ObjectProvider<NotificationEmailEnqueuer> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(email);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var at = Instant.parse("2026-10-22T00:00:00Z");
        var service =
                new NotificationService(repository, preferences, Clock.fixed(at, ZoneOffset.UTC), manager, provider);
        service.create(
                "u", NotificationCategory.REVIEW, "REVIEW_APPROVED", "private", "private", "SKILL", 1L, "source", at);
        verifyNoInteractions(repository);
        var command = org.mockito.ArgumentCaptor.forClass(NotificationEmailCommand.class);
        verify(email).enqueue(command.capture());
        assertThat(command.getValue().eventKey()).isEqualTo("REVIEW_APPROVED:source");
        assertThat(command.getValue().occurredAt()).isEqualTo(at);
        when(preferences.isEnabled("u", NotificationCategory.REVIEW, NotificationChannel.IN_APP)).thenReturn(true);
        doThrow(new IllegalStateException("inbox failed")).when(email).enqueue(any());
        assertThatCode(()
                               -> service.create("u", NotificationCategory.REVIEW, "REVIEW_APPROVED", "title", "body",
                                       "SKILL", 1L, "another", at))
                .doesNotThrowAnyException();
        verify(repository).save(any(Notification.class));
        verify(manager, times(2)).commit(any());
    }
    @SuppressWarnings("unchecked")
    @Test
    void failedInAppTransactionDoesNotPreventEmailBranch() {
        var repository = mock(NotificationRepository.class);
        var preferences = mock(NotificationPreferenceService.class);
        var email = mock(NotificationEmailEnqueuer.class);
        ObjectProvider<NotificationEmailEnqueuer> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(email);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(preferences.isEnabled(any(), any(), any())).thenReturn(true);
        when(repository.save(any())).thenThrow(new IllegalStateException());
        var service = new NotificationService(repository, preferences, Clock.systemUTC(), manager, provider);
        service.create(
                "u", NotificationCategory.REVIEW, "REVIEW_APPROVED", "title", "body", "SKILL", 1L, "id", Instant.now());
        verify(manager).rollback(any());
        verify(email).enqueue(any());
    }
}
