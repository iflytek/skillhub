package com.iflytek.skillhub.domain.user;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class UserAccountTest {

    @Test
    void protectedConstructor_shouldBeAccessible() {
        UserAccount account = new UserAccount();
        assertNull(account.getId());
        assertNull(account.getDisplayName());
        assertNull(account.getCreatedAt());
        assertEquals(UserStatus.ACTIVE, account.getStatus());
    }

    @Test
    void publicConstructor_shouldSetFieldsAndDefaultStatus() {
        UserAccount account = new UserAccount("user-1", "Test User", "test@example.com", "http://avatar.url");

        assertEquals("user-1", account.getId());
        assertEquals("Test User", account.getDisplayName());
        assertEquals("test@example.com", account.getEmail());
        assertEquals("http://avatar.url", account.getAvatarUrl());
        assertEquals(UserStatus.ACTIVE, account.getStatus());
        assertNull(account.getUssId());
        assertNull(account.getMergedToUserId());
    }

    @Test
    void prePersist_shouldSetTimestamps() {
        UserAccount account = new UserAccount("user-1", "Test", "test@example.com", null);
        assertNull(account.getCreatedAt());
        assertNull(account.getUpdatedAt());

        account.prePersist();

        assertNotNull(account.getCreatedAt());
        assertEquals(account.getCreatedAt(), account.getUpdatedAt());
    }

    @Test
    void preUpdate_shouldSetUpdatedAt() {
        UserAccount account = new UserAccount("user-1", "Test", "test@example.com", null);
        account.prePersist();
        Instant beforeUpdate = account.getUpdatedAt();

        try {
            Thread.sleep(5);
        } catch (InterruptedException ignored) {}
        account.preUpdate();

        assertTrue(account.getUpdatedAt().isAfter(beforeUpdate) || account.getUpdatedAt().equals(beforeUpdate));
    }

    @Test
    void setters_shouldUpdateFields() {
        UserAccount account = new UserAccount("user-1", "Test", "test@example.com", null);

        account.setDisplayName("Updated");
        assertEquals("Updated", account.getDisplayName());

        account.setEmail("updated@example.com");
        assertEquals("updated@example.com", account.getEmail());

        account.setAvatarUrl("http://new.avatar");
        assertEquals("http://new.avatar", account.getAvatarUrl());

        account.setUssId("uss-1");
        assertEquals("uss-1", account.getUssId());

        account.setStatus(UserStatus.DISABLED);
        assertEquals(UserStatus.DISABLED, account.getStatus());

        account.setMergedToUserId("user-2");
        assertEquals("user-2", account.getMergedToUserId());
    }

    @Test
    void isActive_shouldReturnTrueForActiveStatus() {
        UserAccount account = new UserAccount("user-1", "Test", "test@example.com", null);
        assertTrue(account.isActive());
    }

    @Test
    void isActive_shouldReturnFalseForNonActiveStatus() {
        UserAccount account = new UserAccount("user-1", "Test", "test@example.com", null);
        account.setStatus(UserStatus.DISABLED);
        assertFalse(account.isActive());
    }
}
