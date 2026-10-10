package com.iflytek.skillhub.domain.social;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "skill_rating",
    uniqueConstraints = @UniqueConstraint(columnNames = {"skill_id", "user_id"}))
public class SkillRating {
    private static final int MAX_REVIEW_LENGTH = 2000;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private Short score;

    @Column(name = "review_text", length = MAX_REVIEW_LENGTH)
    private String reviewText;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 16)
    private SkillReviewStatus reviewStatus = SkillReviewStatus.VISIBLE;

    @Column(name = "moderated_by", length = 128)
    private String moderatedBy;

    @Column(name = "moderated_at")
    private Instant moderatedAt;

    @Column(name = "moderation_reason", length = 500)
    private String moderationReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SkillRating() {}

    public SkillRating(Long skillId, String userId, short score) {
        if (score < 1 || score > 5) throw new DomainBadRequestException("error.rating.score.invalid");
        this.skillId = skillId;
        this.userId = userId;
        this.score = score;
    }

    public void updateScore(short newScore) {
        validateScore(newScore);
        this.score = newScore;
        this.updatedAt = Instant.now(Clock.systemUTC());
    }

    public void updateReview(short newScore, String newReviewText) {
        validateScore(newScore);
        this.score = newScore;
        this.reviewText = normalizeReviewText(newReviewText);
        this.updatedAt = Instant.now(Clock.systemUTC());
    }

    public void clearReview() {
        this.reviewText = null;
        this.updatedAt = Instant.now(Clock.systemUTC());
    }

    public void hideReview(String moderatorId, String reason) {
        ensureReviewExists();
        this.reviewStatus = SkillReviewStatus.HIDDEN;
        this.moderatedBy = moderatorId;
        this.moderatedAt = Instant.now(Clock.systemUTC());
        this.moderationReason = normalizeReason(reason);
    }

    public void restoreReview(String moderatorId) {
        ensureReviewExists();
        this.reviewStatus = SkillReviewStatus.VISIBLE;
        this.moderatedBy = moderatorId;
        this.moderatedAt = Instant.now(Clock.systemUTC());
        this.moderationReason = null;
    }

    public boolean hasReview() {
        return reviewText != null && !reviewText.isBlank();
    }

    private static void validateScore(short value) {
        if (value < 1 || value > 5) {
            throw new DomainBadRequestException("error.rating.score.invalid");
        }
    }

    private static String normalizeReviewText(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainBadRequestException("error.skillReview.text.required");
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_REVIEW_LENGTH) {
            throw new DomainBadRequestException("error.skillReview.text.tooLong", MAX_REVIEW_LENGTH);
        }
        return normalized;
    }

    private static String normalizeReason(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 500) {
            throw new DomainBadRequestException("error.skillReview.reason.tooLong", 500);
        }
        return normalized;
    }

    private void ensureReviewExists() {
        if (!hasReview()) {
            throw new DomainBadRequestException("error.skillReview.notFound");
        }
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now(Clock.systemUTC());
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now(Clock.systemUTC());
    }

    // getters
    public Long getId() { return id; }
    public Long getLockVersion() { return lockVersion; }
    public Long getSkillId() { return skillId; }
    public String getUserId() { return userId; }
    public Short getScore() { return score; }
    public String getReviewText() { return reviewText; }
    public SkillReviewStatus getReviewStatus() { return reviewStatus; }
    public String getModeratedBy() { return moderatedBy; }
    public Instant getModeratedAt() { return moderatedAt; }
    public String getModerationReason() { return moderationReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
