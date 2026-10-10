package com.iflytek.skillhub.domain.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditLogService auditLogService;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-03-18T02:03:04Z"), ZoneOffset.UTC);
        auditLogService = new AuditLogService(auditLogRepository, clock);
    }

    @Test
    void record_usesInjectedClockForCreatedAt() {
        when(auditLogRepository.save(any(AuditLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuditLog result = auditLogService.record(
                "user-1",
                "SKILL_PUBLISH",
                "SKILL",
                7L,
                "req-1",
                "127.0.0.1",
                "JUnit",
                "{\"version\":\"1.0.0\"}"
        );

        assertThat(result.getCreatedAt()).isEqualTo(Instant.now(clock));
        assertThat(result.getAction()).isEqualTo("SKILL_PUBLISH");
        assertThat(result.getTargetId()).isEqualTo(7L);
    }
}
