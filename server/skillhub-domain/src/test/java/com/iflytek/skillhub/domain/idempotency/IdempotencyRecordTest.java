package com.iflytek.skillhub.domain.idempotency;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class IdempotencyRecordTest {

    @Test
    void protectedConstructor_shouldBeAccessible() {
        IdempotencyRecord record = new IdempotencyRecord();
        assertNull(record.getRequestId());
        assertNull(record.getResourceType());
        assertNull(record.getResourceId());
        assertNull(record.getStatus());
        assertNull(record.getResponseStatusCode());
        assertNull(record.getCreatedAt());
        assertNull(record.getExpiresAt());
    }

    @Test
    void publicConstructor_shouldSetAllFields() {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant expiresAt = Instant.parse("2026-01-02T00:00:00Z");
        IdempotencyRecord record = new IdempotencyRecord(
                "req-1", "skill", 1L, IdempotencyStatus.COMPLETED, 200, createdAt, expiresAt);

        assertEquals("req-1", record.getRequestId());
        assertEquals("skill", record.getResourceType());
        assertEquals(1L, record.getResourceId());
        assertEquals(IdempotencyStatus.COMPLETED, record.getStatus());
        assertEquals(200, record.getResponseStatusCode());
        assertEquals(createdAt, record.getCreatedAt());
        assertEquals(expiresAt, record.getExpiresAt());
    }

    @Test
    void setStatus_shouldUpdateStatus() {
        IdempotencyRecord record = new IdempotencyRecord(
                "req-1", "skill", 1L, IdempotencyStatus.PROCESSING, null, Instant.now(), Instant.now());

        record.setStatus(IdempotencyStatus.COMPLETED);

        assertEquals(IdempotencyStatus.COMPLETED, record.getStatus());
    }

    @Test
    void setResponseStatusCode_shouldUpdateCode() {
        IdempotencyRecord record = new IdempotencyRecord(
                "req-1", "skill", 1L, IdempotencyStatus.COMPLETED, 200, Instant.now(), Instant.now());

        record.setResponseStatusCode(201);

        assertEquals(201, record.getResponseStatusCode());
    }

    @Test
    void setStatus_shouldUpdateStatus_fromProcessing() {
        IdempotencyRecord record = new IdempotencyRecord(
                "req-1", "skill", 1L, IdempotencyStatus.PROCESSING, null, Instant.now(), Instant.now());

        record.setStatus(IdempotencyStatus.COMPLETED);

        assertEquals(IdempotencyStatus.COMPLETED, record.getStatus());
    }

    @Test
    void publicConstructor_shouldHandleNullResourceId() {
        IdempotencyRecord record = new IdempotencyRecord(
                "req-1", "skill", null, IdempotencyStatus.PROCESSING, null, Instant.now(), Instant.now());
        assertNull(record.getResourceId());
    }
}
