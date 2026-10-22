package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.email.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class NotificationEmailPreferencesTest {
    @Test
    void defaultsAreOffAndFourCategoriesSupportAllModes() {
        var repository = mock(NotificationEmailPreferenceRepository.class);
        var service = new NotificationEmailPreferenceService(repository);
        assertThat(service.current("u"))
                .hasSize(4)
                .allSatisfy((category, mode) -> assertThat(mode).isEqualTo(NotificationEmailMode.OFF));
        for (var mode : NotificationEmailMode.values()) {
            var rows = Arrays.stream(NotificationCategory.values())
                               .map(category -> {
                                   var row = new NotificationEmailPreference("u", category);
                                   row.setMode(mode);
                                   return row;
                               })
                               .toList();
            when(repository.findByUserIdOrderByCategoryAsc("u")).thenReturn(rows);
            when(repository.lockAll("u")).thenReturn(rows);
            assertThat(service.current("u")).allSatisfy((category, actual) -> assertThat(actual).isEqualTo(mode));
            assertThat(service.lockCurrent("u")).allSatisfy((category, actual) -> assertThat(actual).isEqualTo(mode));
        }
    }
}
