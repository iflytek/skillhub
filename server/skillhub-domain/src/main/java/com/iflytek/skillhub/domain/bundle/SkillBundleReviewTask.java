package com.iflytek.skillhub.domain.bundle;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Bundle-version review task. Mirrors the existing {@code review_task} state machine
 * but stays decoupled from skill review since the auditable artifact is a
 * different aggregate.
 */
@Entity
@Table(name = "skill_bundle_review_task")
public class SkillBundleReviewTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bundle_version_id", nullable = false, unique = true)
    private Long bundleVersionId;

    @Column(name = "namespace_id", nullable = false)
    private Long namespaceId;

    @Column(nullable = false, length = 32)
    private String status = "PENDING";

    @Version
    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "submitted_by", nullable = false, length = 128)
    private String submittedBy;

    @Column(name = "reviewed_by", length = 128)
    private String reviewedBy;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected SkillBundleReviewTask() {}

    public SkillBundleReviewTask(Long bundleVersionId, Long namespaceId, String submittedBy) {
        this.bundleVersionId = bundleVersionId;
        this.namespaceId = namespaceId;
        this.submittedBy = submittedBy;
    }

    public Long getId() { return id; }
    public Long getBundleVersionId() { return bundleVersionId; }
    public Long getNamespaceId() { return namespaceId; }
    public String getStatus() { return status; }
    public Integer getVersion() { return version; }
    public String getSubmittedBy() { return submittedBy; }
    public String getReviewedBy() { return reviewedBy; }
    public String getReviewComment() { return reviewComment; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getReviewedAt() { return reviewedAt; }

    public void setStatus(String status) { this.status = status; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
}
