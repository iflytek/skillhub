package com.iflytek.skillhub.domain.auth;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PasswordResetRequestTest {

    @Test
    void protectedConstructor_shouldBeAccessible() {
        PasswordResetRequest request = new PasswordResetRequest();
        assertNull(request.getId());
        assertNull(request.getUserId());
        assertNull(request.getCreatedAt());
    }

    @Test
    void publicConstructor_shouldSetAllFields() {
        Instant expiresAt = Instant.parse("2026-12-31T23:59:59Z");
        PasswordResetRequest request = new PasswordResetRequest(
                "user-1", "user@example.com", "hash123", expiresAt, true, "admin-1");

        assertNull(request.getId());
        assertEquals("user-1", request.getUserId());
        assertEquals("user@example.com", request.getEmail());
        assertEquals("hash123", request.getCodeHash());
        assertEquals(expiresAt, request.getExpiresAt());
        assertTrue(request.isRequestedByAdmin());
        assertEquals("admin-1", request.getRequestedByUserId());
        assertNull(request.getConsumedAt());
    }

    @Test
    void prePersist_shouldSetCreatedAt_whenNull() {
        PasswordResetRequest request = new PasswordResetRequest(
                "user-1", "user@example.com", "hash123", Instant.now(), false, null);
        assertNull(request.getCreatedAt());

        request.prePersist();

        assertNotNull(request.getCreatedAt());
    }

    @Test
    void prePersist_shouldNotOverrideExistingCreatedAt() {
        Instant existing = Instant.parse("2026-01-01T00:00:00Z");
        PasswordResetRequest request = new PasswordResetRequest(
                "user-1", "user@example.com", "hash123", Instant.now(), false, null);
        try {
            java.lang.reflect.Field field = PasswordResetRequest.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(request, existing);
        } catch (Exception e) {
            fail(e);
        }

        request.prePersist();

        assertEquals(existing, request.getCreatedAt());
    }

    @Test
    void markConsumed_shouldSetConsumedAt() {
        Instant consumedAt = Instant.parse("2026-06-01T12:00:00Z");
        PasswordResetRequest request = new PasswordResetRequest(
                "user-1", "user@example.com", "hash123", Instant.now(), false, null);

        request.markConsumed(consumedAt);

        assertEquals(consumedAt, request.getConsumedAt());
    }

    @Test
    void publicConstructor_shouldHandleNullRequestedByUserId() {
        PasswordResetRequest request = new PasswordResetRequest(
                "user-1", "user@example.com", "hash123", Instant.now(), false, null);
        assertNull(request.getRequestedByUserId());
    }
}
