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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.iflytek.skillhub.domain.authoring.FixSuggestion;

/**
 * A single problem or observation produced by one validation layer, optionally carrying
 * a machine-applicable fix suggestion (patch payload) that the author can preview,
 * confirm, and apply to produce a new draft revision.
 */
@Entity
@Table(name = "validation_finding")
public class ValidationFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ValidationLayer layer;

    @Column(name = "rule_code", nullable = false, length = 64)
    private String ruleCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FindingSeverity severity;

    @Column(name = "file_path", length = 512)
    private String filePath;

    @Column(length = 128)
    private String location;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    /** Machine-applicable fix payload; null when no machine-applicable fix exists. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "suggestion")
    private FixSuggestion suggestion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FindingStatus status = FindingStatus.OPEN;

    @Column(name = "applied_revision")
    private Integer appliedRevision;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ValidationFinding() {
    }

    public ValidationFinding(Long runId, ValidationLayer layer, String ruleCode,
                             FindingSeverity severity, String filePath, String location,
                             String message, FixSuggestion suggestion) {
        this.runId = runId;
        this.layer = layer;
        this.ruleCode = ruleCode;
        this.severity = severity;
        this.filePath = filePath;
        this.location = location;
        this.message = message;
        this.suggestion = suggestion;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now(Clock.systemUTC());
    }

    public void markApplied(int appliedRevision) {
        this.status = FindingStatus.APPLIED;
        this.appliedRevision = appliedRevision;
    }

    public void markDismissed() {
        this.status = FindingStatus.DISMISSED;
    }

    public boolean hasSuggestion() {
        return suggestion != null && !suggestion.safePatches().isEmpty();
    }

    // Getters

    public Long getId() {
        return id;
    }

    public Long getRunId() {
        return runId;
    }

    public ValidationLayer getLayer() {
        return layer;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public FindingSeverity getSeverity() {
        return severity;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getLocation() {
        return location;
    }

    public String getMessage() {
        return message;
    }

    public FixSuggestion getSuggestion() {
        return suggestion;
    }

    public FindingStatus getStatus() {
        return status;
    }

    public Integer getAppliedRevision() {
        return appliedRevision;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
