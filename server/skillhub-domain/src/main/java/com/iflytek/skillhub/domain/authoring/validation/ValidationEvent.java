package com.iflytek.skillhub.domain.authoring.validation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One persisted run event. {@code seq} is a per-run monotonic counter used both for
 * ordered display and for SSE resume (Last-Event-ID) and polling (afterSeq) cursors.
 */
@Entity
@Table(name = "validation_event",
        uniqueConstraints = @jakarta.persistence.UniqueConstraint(
                name = "uq_validation_event_seq", columnNames = {"run_id", "seq"}))
public class ValidationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(nullable = false)
    private Integer seq;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private ValidationEventType eventType;

    @Column(length = 32)
    private String phase;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload")
    private Map<String, Object> payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ValidationEvent() {
    }

    public ValidationEvent(Long runId, Integer seq, ValidationEventType eventType,
                           String phase, Map<String, Object> payload) {
        this.runId = runId;
        this.seq = seq;
        this.eventType = eventType;
        this.phase = phase;
        this.payload = payload;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now(Clock.systemUTC());
    }

    // Getters

    public Long getId() {
        return id;
    }

    public Long getRunId() {
        return runId;
    }

    public Integer getSeq() {
        return seq;
    }

    public ValidationEventType getEventType() {
        return eventType;
    }

    public String getPhase() {
        return phase;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
