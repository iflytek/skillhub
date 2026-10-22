package com.iflytek.skillhub.domain.event;
public record ReportResolvedEvent(Long reportId, Long skillId, String handlerId, String reporterId, String action, String eventId, java.time.Instant occurredAt) {
    public ReportResolvedEvent(Long reportId, Long skillId, String handlerId, String reporterId, String action) {
        this(reportId,skillId,handlerId,reporterId,action,java.util.UUID.randomUUID().toString(),java.time.Instant.now());
    }
    public ReportResolvedEvent {
        java.util.Objects.requireNonNull(eventId);
        java.util.Objects.requireNonNull(occurredAt);
    }
}
