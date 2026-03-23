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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "security_audit")
public class SecurityAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_version_id", nullable = false)
    private Long skillVersionId;

    @Column(name = "scan_id", length = 100)
    private String scanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scanner_type", nullable = false, length = 50)
    private ScannerType scannerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SecurityVerdict verdict;

    @Column(name = "is_safe", nullable = false)
    private Boolean isSafe;

    @Column(name = "max_severity", length = 20)
    private String maxSeverity;

    @Column(name = "findings_count", nullable = false)
    private Integer findingsCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "findings", columnDefinition = "jsonb")
    private String findings;

    @Column(name = "scan_duration_seconds")
    private Double scanDurationSeconds;

    @Column(name = "scanned_at")
    private Instant scannedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected SecurityAudit() {
    }

    public SecurityAudit(Long skillVersionId, ScannerType scannerType) {
        this.skillVersionId = skillVersionId;
        this.scannerType = scannerType;
        this.verdict = SecurityVerdict.SUSPICIOUS;
        this.isSafe = false;
        this.findingsCount = 0;
        this.findings = "[]";
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now(Clock.systemUTC());
    }

    public Long getId() {
        return id;
    }

    public Long getSkillVersionId() {
        return skillVersionId;
    }

    public String getScanId() {
        return scanId;
    }

    public ScannerType getScannerType() {
        return scannerType;
    }

    public SecurityVerdict getVerdict() {
        return verdict;
    }

    public Boolean getIsSafe() {
        return isSafe;
    }

    public String getMaxSeverity() {
        return maxSeverity;
    }

    public Integer getFindingsCount() {
        return findingsCount;
    }

    public String getFindings() {
        return findings;
    }

    public Double getScanDurationSeconds() {
        return scanDurationSeconds;
    }

    public Instant getScannedAt() {
        return scannedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setScanId(String scanId) {
        this.scanId = scanId;
    }

    public void setVerdict(SecurityVerdict verdict) {
        this.verdict = verdict;
    }

    public void setIsSafe(Boolean safe) {
        isSafe = safe;
    }

    public void setMaxSeverity(String maxSeverity) {
        this.maxSeverity = maxSeverity;
    }

    public void setFindingsCount(Integer findingsCount) {
        this.findingsCount = findingsCount;
    }

    public void setFindings(String findings) {
        this.findings = findings;
    }

    public void setScanDurationSeconds(Double scanDurationSeconds) {
        this.scanDurationSeconds = scanDurationSeconds;
    }

    public void setScannedAt(Instant scannedAt) {
        this.scannedAt = scannedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    /**
     * Soft delete this audit record.
     * Sets the deleted_at timestamp to mark the record as logically deleted.
     */
    public void markAsDeleted() {
        this.deletedAt = Instant.now(Clock.systemUTC());
    }

    /**
     * Restore a soft-deleted audit record.
     * Clears the deleted_at timestamp to mark the record as active again.
     */
    public void restore() {
        this.deletedAt = null;
    }

    /**
     * Check if this audit record is soft-deleted.
     * @return true if deleted_at is not null, false otherwise
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }
}
