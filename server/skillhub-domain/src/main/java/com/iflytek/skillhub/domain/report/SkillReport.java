package com.iflytek.skillhub.domain.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "skill_report")
public class SkillReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "namespace_id", nullable = false)
    private Long namespaceId;

    @Column(name = "reporter_id", nullable = false, length = 128)
    private String reporterId;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SkillReportStatus status = SkillReportStatus.PENDING;

    @Column(name = "handled_by", length = 128)
    private String handledBy;

    @Column(name = "handle_comment", columnDefinition = "TEXT")
    private String handleComment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    protected SkillReport() {
    }

    public SkillReport(Long skillId, Long namespaceId, String reporterId, String reason, String details) {
        this.skillId = skillId;
        this.namespaceId = namespaceId;
        this.reporterId = reporterId;
        this.reason = reason;
        this.details = details;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getSkillId() {
        return skillId;
    }

    public Long getNamespaceId() {
        return namespaceId;
    }

    public String getReporterId() {
        return reporterId;
    }

    public String getReason() {
        return reason;
    }

    public String getDetails() {
        return details;
    }

    public SkillReportStatus getStatus() {
        return status;
    }

    public void setStatus(SkillReportStatus status) {
        this.status = status;
    }

    public String getHandledBy() {
        return handledBy;
    }

    public void setHandledBy(String handledBy) {
        this.handledBy = handledBy;
    }

    public String getHandleComment() {
        return handleComment;
    }

    public void setHandleComment(String handleComment) {
        this.handleComment = handleComment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getHandledAt() {
        return handledAt;
    }

    public void setHandledAt(LocalDateTime handledAt) {
        this.handledAt = handledAt;
    }
}
