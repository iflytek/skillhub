package com.iflytek.skillhub.domain.user;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

/**
 * Represents a user-initiated profile change request.
 *
 * <p>Each request captures a batch of field changes as JSONB (e.g. displayName, avatarUrl),
 * along with the previous values for audit and rollback purposes.
 *
 * <p>Depending on the moderation configuration, a request may be:
 * <ul>
 *   <li>Immediately approved (no moderation)</li>
 *   <li>Rejected by machine review</li>
 *   <li>Queued for human review (PENDING)</li>
 * </ul>
 *
 * @see ProfileChangeStatus for the full lifecycle
 */
@Entity
@Table(name = "profile_change_request")
public class ProfileChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user who initiated this change request. */
    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    /** Requested changes as JSON, e.g. {"displayName": "new name"}. */
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String changes;

    /** Snapshot of previous values before this change, e.g. {"displayName": "old name"}. */
    @Column(name = "old_values")
    @JdbcTypeCode(SqlTypes.JSON)
    private String oldValues;

    /** Current status in the review lifecycle. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProfileChangeStatus status = ProfileChangeStatus.PENDING;

    /** Machine review outcome: PASS, FAIL, or SKIPPED. */
    @Column(name = "machine_result", length = 32)
    private String machineResult;

    /** Reason provided by machine review when rejected. */
    @Column(name = "machine_reason")
    private String machineReason;

    /** User ID of the human reviewer who acted on this request. */
    @Column(name = "reviewer_id", length = 128)
    private String reviewerId;

    /** Comment left by the human reviewer. */
    @Column(name = "review_comment")
    private String reviewComment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Timestamp when human review was completed. */
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected ProfileChangeRequest() {}

    public ProfileChangeRequest(String userId, String changes, String oldValues,
                                 ProfileChangeStatus status, String machineResult,
                                 String machineReason) {
        this.userId = userId;
        this.changes = changes;
        this.oldValues = oldValues;
        this.status = status;
        this.machineResult = machineResult;
        this.machineReason = machineReason;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    // -- Getters --

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public String getChanges() { return changes; }
    public String getOldValues() { return oldValues; }
    public ProfileChangeStatus getStatus() { return status; }
    public String getMachineResult() { return machineResult; }
    public String getMachineReason() { return machineReason; }
    public String getReviewerId() { return reviewerId; }
    public String getReviewComment() { return reviewComment; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReviewedAt() { return reviewedAt; }

    // -- Setters (only for mutable fields) --

    public void setStatus(ProfileChangeStatus status) { this.status = status; }
    public void setReviewerId(String reviewerId) { this.reviewerId = reviewerId; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
}
