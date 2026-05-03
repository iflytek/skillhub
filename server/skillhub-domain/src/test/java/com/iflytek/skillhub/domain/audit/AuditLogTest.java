package com.iflytek.skillhub.domain.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuditLogTest {

    @Test
    void constructorsAndGettersExposeAuditPayload() {
        AuditLog empty = new AuditLog();

        assertThat(empty.getId()).isNull();
        assertThat(empty.getActorUserId()).isNull();
        assertThat(empty.getAction()).isNull();
        assertThat(empty.getTargetType()).isNull();
        assertThat(empty.getTargetId()).isNull();
        assertThat(empty.getRequestId()).isNull();
        assertThat(empty.getClientIp()).isNull();
        assertThat(empty.getUserAgent()).isNull();
        assertThat(empty.getDetailJson()).isNull();
        assertThat(empty.getCreatedAt()).isNull();

        Instant createdAt = Instant.parse("2026-05-04T00:00:00Z");
        AuditLog auditLog = new AuditLog(
                "user-1",
                "SKILL_PUBLISH",
                "SKILL",
                42L,
                "req-1",
                "127.0.0.1",
                "JUnit",
                "{\"status\":\"ok\"}",
                createdAt
        );

        assertThat(auditLog.getId()).isNull();
        assertThat(auditLog.getActorUserId()).isEqualTo("user-1");
        assertThat(auditLog.getAction()).isEqualTo("SKILL_PUBLISH");
        assertThat(auditLog.getTargetType()).isEqualTo("SKILL");
        assertThat(auditLog.getTargetId()).isEqualTo(42L);
        assertThat(auditLog.getRequestId()).isEqualTo("req-1");
        assertThat(auditLog.getClientIp()).isEqualTo("127.0.0.1");
        assertThat(auditLog.getUserAgent()).isEqualTo("JUnit");
        assertThat(auditLog.getDetailJson()).isEqualTo("{\"status\":\"ok\"}");
        assertThat(auditLog.getCreatedAt()).isEqualTo(createdAt);
    }
}
