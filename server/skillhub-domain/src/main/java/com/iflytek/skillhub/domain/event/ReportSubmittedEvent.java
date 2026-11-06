package com.iflytek.skillhub.domain.event;
public record ReportSubmittedEvent(Long reportId, Long skillId, String reporterId, String eventId, java.time.Instant occurredAt) {
    public ReportSubmittedEvent(Long reportId, Long skillId, String reporterId) {
        this(reportId,skillId,reporterId,java.util.UUID.randomUUID().toString(),java.time.Instant.now());
    }
    public ReportSubmittedEvent {
        java.util.Objects.requireNonNull(eventId);
        java.util.Objects.requireNonNull(occurredAt);
    }
}
