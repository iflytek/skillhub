package com.iflytek.skillhub.domain.authoring.validation;

import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One immutable validation execution bound to a specific draft revision.
 *
 * <p>Transitions are guarded: only the executor moves a run forward and only
 * active runs can be cancelled or timed out. {@code cancelRequested} is a
 * cooperative flag set by the cancel API and honored between tasks.
 */
@Entity
@Table(name = "validation_run")
public class ValidationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "draft_id", nullable = false)
    private Long draftId;

    @Column(name = "draft_revision", nullable = false)
    private Integer draftRevision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ValidationRunStatus status = ValidationRunStatus.QUEUED;

    @Column(name = "cancel_requested", nullable = false)
    private boolean cancelRequested;

    @Column(name = "error_count", nullable = false)
    private Integer errorCount = 0;

    @Column(name = "warning_count", nullable = false)
    private Integer warningCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary")
    private Map<String, Object> summary;

    @Column(name = "triggered_by", nullable = false, length = 128)
    private String triggeredBy;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long optimisticVersion;

    protected ValidationRun() {
    }

    public ValidationRun(Long draftId, Integer draftRevision, String triggeredBy) {
        this.draftId = draftId;
        this.draftRevision = draftRevision;
        this.triggeredBy = triggeredBy;
        this.status = ValidationRunStatus.QUEUED;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now(Clock.systemUTC());
    }

    public void markPreparing() {
        requireActive("markPreparing");
        if (status != ValidationRunStatus.QUEUED) {
            throw new DomainConflictException("error.authoring.run.notQueued", id);
        }
        status = ValidationRunStatus.PREPARING;
    }

    public void markRunning(Instant now) {
        requireActive("markRunning");
        status = ValidationRunStatus.RUNNING;
        startedAt = now;
    }

    /**
     * Settles the run into a terminal status and records finding counters plus a
     * machine-readable summary. The status must be one of the natural outcomes
     * (SUCCEEDED / FAILED / CANCELLED / TIMED_OUT).
     */
    public void finish(ValidationRunStatus terminalStatus, int errorCount, int warningCount,
                       Map<String, Object> summary, Instant now) {
        if (terminalStatus == null || !terminalStatus.isTerminal()) {
            throw new IllegalArgumentException("Terminal status required: " + terminalStatus);
        }
        requireActive("finish");
        status = terminalStatus;
        this.errorCount = errorCount;
        this.warningCount = warningCount;
        this.summary = summary;
        this.finishedAt = now;
    }

    /** Idempotent cooperative cancel request; repeated calls keep the first state. */
    public void requestCancel() {
        if (isActive()) {
            cancelRequested = true;
        }
    }

    public boolean isCancelRequested() {
        return cancelRequested;
    }

    public boolean isActive() {
        return status.isActive();
    }

    private void requireActive(String action) {
        if (status.isTerminal()) {
            throw new DomainConflictException("error.authoring.run.terminal", id, status);
        }
    }

    // Getters

    public Long getId() {
        return id;
    }

    public Long getDraftId() {
        return draftId;
    }

    public Integer getDraftRevision() {
        return draftRevision;
    }

    public ValidationRunStatus getStatus() {
        return status;
    }

    public Integer getErrorCount() {
        return errorCount;
    }

    public Integer getWarningCount() {
        return warningCount;
    }

    public Map<String, Object> getSummary() {
        return summary;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getOptimisticVersion() {
        return optimisticVersion;
    }
}
