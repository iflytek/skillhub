package com.iflytek.skillhub.domain.review;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "promotion_request")
public class PromotionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_skill_id", nullable = false)
    private Long sourceSkillId;

    @Column(name = "source_version_id", nullable = false)
    private Long sourceVersionId;

    @Column(name = "target_namespace_id", nullable = false)
    private Long targetNamespaceId;

    @Column(name = "target_skill_id")
    private Long targetSkillId;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_kind", nullable = false)
    private PromotionRequestKind requestKind = PromotionRequestKind.INITIAL;

    @Column(name = "target_version_id")
    private Long targetVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewTaskStatus status = ReviewTaskStatus.PENDING;

    @Version
    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "submitted_by", nullable = false)
    private String submittedBy;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected PromotionRequest() {}

    public PromotionRequest(Long sourceSkillId, Long sourceVersionId,
                            Long targetNamespaceId, String submittedBy) {
        this.sourceSkillId = sourceSkillId;
        this.sourceVersionId = sourceVersionId;
        this.targetNamespaceId = targetNamespaceId;
        this.submittedBy = submittedBy;
    }

    public Long getId() { return id; }

    public Long getSourceSkillId() { return sourceSkillId; }

    public Long getSourceVersionId() { return sourceVersionId; }

    public Long getTargetNamespaceId() { return targetNamespaceId; }

    public Long getTargetSkillId() { return targetSkillId; }

    public PromotionRequestKind getRequestKind() { return requestKind; }

    public void setRequestKind(PromotionRequestKind requestKind) { this.requestKind = requestKind; }

    public Long getTargetVersionId() { return targetVersionId; }

    public void setTargetVersionId(Long targetVersionId) { this.targetVersionId = targetVersionId; }

    public void setTargetSkillId(Long targetSkillId) {
        this.targetSkillId = targetSkillId;
    }

    public ReviewTaskStatus getStatus() { return status; }

    public void setStatus(ReviewTaskStatus status) { this.status = status; }

    public Integer getVersion() { return version; }

    public String getSubmittedBy() { return submittedBy; }

    public String getReviewedBy() { return reviewedBy; }

    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }

    public String getReviewComment() { return reviewComment; }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }

    public Instant getSubmittedAt() { return submittedAt; }

    public Instant getReviewedAt() { return reviewedAt; }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
