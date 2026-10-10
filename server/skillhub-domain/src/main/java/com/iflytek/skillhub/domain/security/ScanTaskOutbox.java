package com.iflytek.skillhub.domain.security;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "scan_task_outbox")
public class ScanTaskOutbox {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "task_id", nullable = false, unique = true, length = 100)
    private String taskId;
    @Column(name = "version_id", nullable = false)
    private Long versionId;
    @Column(name = "skill_path", length = 1000)
    private String skillPath;
    @Column(name = "bundle_key", length = 1000)
    private String bundleKey;
    @Column(name = "publisher_id", length = 255)
    private String publisherId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> metadata;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private ScanTaskOutboxStatus status;
    @Column(name = "retry_count", nullable = false)
    private int retryCount;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "lease_until")
    private Instant leaseUntil;
    @Column(name = "last_error", length = 2000)
    private String lastError;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version @Column(nullable = false)
    private long entityVersion;

    protected ScanTaskOutbox() { }

    public ScanTaskOutbox(ScanTask task) {
        this.taskId = task.taskId();
        this.versionId = task.versionId();
        this.skillPath = task.skillPath();
        this.bundleKey = task.bundleKey();
        this.publisherId = task.publisherId();
        this.metadata = task.metadata() == null ? Map.of() : Map.copyOf(task.metadata());
        this.status = ScanTaskOutboxStatus.PENDING;
        Instant taskCreatedAt = Instant.ofEpochMilli(task.createdAtMillis());
        this.nextAttemptAt = taskCreatedAt;
        this.createdAt = taskCreatedAt;
        this.updatedAt = taskCreatedAt;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now(Clock.systemUTC());
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (nextAttemptAt == null) nextAttemptAt = now;
    }

    public ScanTask toScanTask() {
        return new ScanTask(taskId, versionId, skillPath, bundleKey, publisherId,
                createdAt.toEpochMilli(), metadata == null ? Map.of() : Map.copyOf(metadata));
    }

    public boolean claim(Instant now, Duration lease) {
        if (status != ScanTaskOutboxStatus.PENDING
                && !(status == ScanTaskOutboxStatus.SENDING && leaseUntil != null && leaseUntil.isBefore(now))) return false;
        status = ScanTaskOutboxStatus.SENDING;
        leaseUntil = now.plus(lease);
        updatedAt = now;
        return true;
    }

    public void markSent(Instant now) {
        status = ScanTaskOutboxStatus.SENT;
        leaseUntil = null;
        lastError = null;
        updatedAt = now;
    }

    public void markFailed(Instant now, String error) {
        retryCount++;
        status = ScanTaskOutboxStatus.FAILED;
        leaseUntil = null;
        lastError = truncateError(error);
        updatedAt = now;
    }

    public void markRetry(Instant now, Duration delay, String error) {
        retryCount++;
        status = ScanTaskOutboxStatus.PENDING;
        nextAttemptAt = now.plus(delay);
        leaseUntil = null;
        lastError = truncateError(error);
        updatedAt = now;
    }

    public Long getId() { return id; }
    public String getTaskId() { return taskId; }
    public Long getVersionId() { return versionId; }
    public ScanTaskOutboxStatus getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getLeaseUntil() { return leaseUntil; }

    private String truncateError(String error) {
        return error == null ? null : error.substring(0, Math.min(error.length(), 2000));
    }

    public Instant getCreatedAt() { return createdAt; }
}
