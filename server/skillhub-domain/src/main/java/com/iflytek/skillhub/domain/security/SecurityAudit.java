package com.iflytek.skillhub.domain.security;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

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

    @Column(name = "scanner_type", nullable = false, length = 50)
    private String scannerType;

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
    private LocalDateTime scannedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected SecurityAudit() {
    }

    public SecurityAudit(Long skillVersionId, String scannerType) {
        this.skillVersionId = skillVersionId;
        this.scannerType = scannerType;
        this.verdict = SecurityVerdict.SUSPICIOUS;
        this.isSafe = false;
        this.findings = "[]";
        this.findingsCount = 0;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters
    public Long getId() { return id; }
    public Long getSkillVersionId() { return skillVersionId; }
    public String getScanId() { return scanId; }
    public String getScannerType() { return scannerType; }
    public SecurityVerdict getVerdict() { return verdict; }
    public Boolean getIsSafe() { return isSafe; }
    public String getMaxSeverity() { return maxSeverity; }
    public Integer getFindingsCount() { return findingsCount; }
    public String getFindings() { return findings; }
    public Double getScanDurationSeconds() { return scanDurationSeconds; }
    public LocalDateTime getScannedAt() { return scannedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // Setters
    public void setScanId(String scanId) { this.scanId = scanId; }
    public void setVerdict(SecurityVerdict verdict) { this.verdict = verdict; }
    public void setIsSafe(Boolean isSafe) { this.isSafe = isSafe; }
    public void setMaxSeverity(String maxSeverity) { this.maxSeverity = maxSeverity; }
    public void setFindingsCount(Integer findingsCount) { this.findingsCount = findingsCount; }
    public void setFindings(String findings) { this.findings = findings; }
    public void setScanDurationSeconds(Double scanDurationSeconds) { this.scanDurationSeconds = scanDurationSeconds; }
    public void setScannedAt(LocalDateTime scannedAt) { this.scannedAt = scannedAt; }
}
