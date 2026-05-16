package com.iflytek.skillhub.domain.promotion;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Operational promotion campaign placing a skill or skill bundle into a slot for a time window.
 *
 * <p>State transitions: DRAFT -&gt; PENDING_REVIEW -&gt; (REJECTED | SCHEDULED -&gt; ACTIVE -&gt; ENDED).
 * Status transitions are guarded by the JPA @Version optimistic lock.
 */
@Entity
@Table(name = "promotion_campaign")
public class PromotionCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32)
    private PromotionTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "target_version_id")
    private Long targetVersionId;

    @Column(name = "slot_code", nullable = false, length = 64)
    private String slotCode;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(length = 512)
    private String subtitle;

    @Column(name = "cover_media_id")
    private Long coverMediaId;

    @Column(name = "demo_media_id")
    private Long demoMediaId;

    @Column(nullable = false)
    private int priority = 50;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PromotionCampaignStatus status = PromotionCampaignStatus.DRAFT;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "submitted_by", nullable = false, length = 128)
    private String submittedBy;

    @Column(name = "reviewed_by", length = 128)
    private String reviewedBy;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Version
    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PromotionCampaign() {}

    public PromotionCampaign(PromotionTargetType targetType, Long targetId, String slotCode,
                             String title, int priority, Instant startsAt, Instant endsAt,
                             String submittedBy) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.slotCode = slotCode;
        this.title = title;
        this.priority = priority;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.submittedBy = submittedBy;
    }

    public Long getId() { return id; }
    public PromotionTargetType getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public Long getTargetVersionId() { return targetVersionId; }
    public String getSlotCode() { return slotCode; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public Long getCoverMediaId() { return coverMediaId; }
    public Long getDemoMediaId() { return demoMediaId; }
    public int getPriority() { return priority; }
    public PromotionCampaignStatus getStatus() { return status; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public String getSubmittedBy() { return submittedBy; }
    public String getReviewedBy() { return reviewedBy; }
    public String getReviewComment() { return reviewComment; }
    public String getReason() { return reason; }
    public Integer getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(PromotionCampaignStatus status) { this.status = status; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public void setCoverMediaId(Long coverMediaId) { this.coverMediaId = coverMediaId; }
    public void setDemoMediaId(Long demoMediaId) { this.demoMediaId = demoMediaId; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public void setReason(String reason) { this.reason = reason; }
    public void setTargetVersionId(Long targetVersionId) { this.targetVersionId = targetVersionId; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
