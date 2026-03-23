package com.iflytek.skillhub.notification.service;

import com.iflytek.skillhub.notification.domain.*;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

    @Mock private NotificationPreferenceRepository preferenceRepository;
    private NotificationPreferenceService service;

    @BeforeEach
    void setUp() {
        service = new NotificationPreferenceService(preferenceRepository);
    }

    @Test
    void isEnabled_shouldReturnTrueByDefault() {
        when(preferenceRepository.findByUserIdAndCategoryAndChannel(
                "user-1", NotificationCategory.REVIEW, NotificationChannel.IN_APP))
                .thenReturn(Optional.empty());
        assertTrue(service.isEnabled("user-1", NotificationCategory.REVIEW, NotificationChannel.IN_APP));
    }

    @Test
    void isEnabled_shouldReturnSavedValue() {
        NotificationPreference pref = new NotificationPreference(
                "user-1", NotificationCategory.REVIEW, NotificationChannel.IN_APP, false);
        when(preferenceRepository.findByUserIdAndCategoryAndChannel(
                "user-1", NotificationCategory.REVIEW, NotificationChannel.IN_APP))
                .thenReturn(Optional.of(pref));
        assertFalse(service.isEnabled("user-1", NotificationCategory.REVIEW, NotificationChannel.IN_APP));
    }

    @Test
    void getPreferences_shouldReturnAllCategoriesWithDefaults() {
        when(preferenceRepository.findByUserId("user-1")).thenReturn(List.of());
        List<NotificationPreferenceService.PreferenceView> prefs = service.getPreferences("user-1");
        assertEquals(NotificationCategory.values().length, prefs.size());
        assertTrue(prefs.stream().allMatch(NotificationPreferenceService.PreferenceView::enabled));
    }

    @Test
    void updatePreference_shouldCreateNewWhenNotExists() {
        when(preferenceRepository.findByUserIdAndCategoryAndChannel(
                "user-1", NotificationCategory.REVIEW, NotificationChannel.IN_APP))
                .thenReturn(Optional.empty());
        when(preferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.updatePreference("user-1", NotificationCategory.REVIEW, NotificationChannel.IN_APP, false);
        verify(preferenceRepository).save(any(NotificationPreference.class));
    }

    @Test
    void updatePreference_shouldUpdateExisting() {
        NotificationPreference pref = new NotificationPreference(
                "user-1", NotificationCategory.REVIEW, NotificationChannel.IN_APP, true);
        when(preferenceRepository.findByUserIdAndCategoryAndChannel(
                "user-1", NotificationCategory.REVIEW, NotificationChannel.IN_APP))
                .thenReturn(Optional.of(pref));
        when(preferenceRepository.save(any())).thenReturn(pref);
        service.updatePreference("user-1", NotificationCategory.REVIEW, NotificationChannel.IN_APP, false);
        assertFalse(pref.isEnabled());
        verify(preferenceRepository).save(pref);
    }

    @Test
    void updatePreferences_shouldRejectDuplicateItems() {
        assertThrows(DomainBadRequestException.class, () -> service.updatePreferences(
                "user-1",
                List.of(
                        new NotificationPreferenceService.PreferenceCommand(NotificationCategory.REVIEW, NotificationChannel.IN_APP, true),
                        new NotificationPreferenceService.PreferenceCommand(NotificationCategory.REVIEW, NotificationChannel.IN_APP, false)
                )
        ));
    }
}
