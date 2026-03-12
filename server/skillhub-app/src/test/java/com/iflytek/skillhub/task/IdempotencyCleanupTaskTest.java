package com.iflytek.skillhub.task;

import com.iflytek.skillhub.domain.idempotency.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyCleanupTaskTest {

    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;

    private IdempotencyCleanupTask cleanupTask;

    @BeforeEach
    void setUp() {
        cleanupTask = new IdempotencyCleanupTask(idempotencyRecordRepository);
    }

    @Test
    void testCleanupExpiredRecords() {
        when(idempotencyRecordRepository.deleteExpired(any(Instant.class))).thenReturn(5);

        cleanupTask.cleanupExpiredRecords();

        verify(idempotencyRecordRepository).deleteExpired(any(Instant.class));
    }

    @Test
    void testCleanupStaleProcessing() {
        when(idempotencyRecordRepository.markStaleAsFailed(any(Instant.class))).thenReturn(3);

        cleanupTask.cleanupStaleProcessing();

        verify(idempotencyRecordRepository).markStaleAsFailed(any(Instant.class));
    }

    @Test
    void testCleanupStaleProcessingWithNoRecords() {
        when(idempotencyRecordRepository.markStaleAsFailed(any(Instant.class))).thenReturn(0);

        cleanupTask.cleanupStaleProcessing();

        verify(idempotencyRecordRepository).markStaleAsFailed(any(Instant.class));
    }
}
