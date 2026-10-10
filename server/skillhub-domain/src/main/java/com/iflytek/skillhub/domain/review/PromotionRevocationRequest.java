package com.iflytek.skillhub.domain.review;

import jakarta.persistence.*;
import java.time.Instant;

/** Immutable skill coordinates are retained even after the global target is deleted. */
@Entity
@Table(name = "promotion_revocation_request")
public class PromotionRevocationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "initial_promotion_request_id", nullable = false)
    private Long initialPromotionRequestId;

    @Column(name = "source_skill_id", nullable = false)
    private Long sourceSkillId;

    @Column(name = "target_skill_id", nullable = false)
    private Long targetSkillId;

    @Column(name = "source_namespace_id", nullable = false)
    private Long sourceNamespaceId;

    @Column(name = "target_namespace_id", nullable = false)
    private Long targetNamespaceId;

    @Column(name = "skill_slug", nullable = false)
    private String skillSlug;

    @Column(name = "submitted_by", nullable = false)
    private String submittedBy;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewTaskStatus status = ReviewTaskStatus.PENDING;

    @Version
    @Column(nullable = false)
    private Integer version = 1;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected PromotionRevocationRequest() {}

    public PromotionRevocationRequest(Long initialPromotionRequestId, Long sourceSkillId,
                                      Long targetSkillId, Long sourceNamespaceId,
                                      Long targetNamespaceId, String skillSlug,
                                      String submittedBy, String reason) {
        this.initialPromotionRequestId = initialPromotionRequestId;
        this.sourceSkillId = sourceSkillId;
        this.targetSkillId = targetSkillId;
        this.sourceNamespaceId = sourceNamespaceId;
        this.targetNamespaceId = targetNamespaceId;
        this.skillSlug = skillSlug;
        this.submittedBy = submittedBy;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public Long getInitialPromotionRequestId() { return initialPromotionRequestId; }
    public Long getSourceSkillId() { return sourceSkillId; }
    public Long getTargetSkillId() { return targetSkillId; }
    public Long getSourceNamespaceId() { return sourceNamespaceId; }
    public Long getTargetNamespaceId() { return targetNamespaceId; }
    public String getSkillSlug() { return skillSlug; }
    public String getSubmittedBy() { return submittedBy; }
    public String getReviewedBy() { return reviewedBy; }
    public ReviewTaskStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public String getReviewComment() { return reviewComment; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getReviewedAt() { return reviewedAt; }

    public void review(ReviewTaskStatus newStatus, String reviewerId, String comment, Instant now) {
        status = newStatus;
        reviewedBy = reviewerId;
        reviewComment = comment;
        reviewedAt = now;
    }
}
