package com.iflytek.skillhub.notification.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NotificationPreferenceTest {

    @Test
    void protectedConstructor_shouldBeAccessible() {
        NotificationPreference pref = new NotificationPreference();
        assertNull(pref.getId());
        assertNull(pref.getUserId());
        assertNull(pref.getCategory());
        assertNull(pref.getChannel());
        assertTrue(pref.isEnabled());
    }

    @Test
    void publicConstructor_shouldSetAllFields() {
        NotificationPreference pref = new NotificationPreference(
                "user-1", NotificationCategory.REVIEW, NotificationChannel.IN_APP, false);

        assertNull(pref.getId());
        assertEquals("user-1", pref.getUserId());
        assertEquals(NotificationCategory.REVIEW, pref.getCategory());
        assertEquals(NotificationChannel.IN_APP, pref.getChannel());
        assertFalse(pref.isEnabled());
    }

    @Test
    void setEnabled_shouldUpdateEnabledFlag() {
        NotificationPreference pref = new NotificationPreference(
                "user-1", NotificationCategory.PUBLISH, NotificationChannel.IN_APP, true);
        assertTrue(pref.isEnabled());

        pref.setEnabled(false);
        assertFalse(pref.isEnabled());
    }
}
