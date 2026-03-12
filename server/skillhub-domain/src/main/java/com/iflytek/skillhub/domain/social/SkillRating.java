package com.iflytek.skillhub.domain.social;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "skill_rating",
    uniqueConstraints = @UniqueConstraint(columnNames = {"skill_id", "user_id"}))
public class SkillRating {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private Short score;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected SkillRating() {}

    public SkillRating(Long skillId, String userId, short score) {
        if (score < 1 || score > 5) throw new IllegalArgumentException("Score must be 1-5");
        this.skillId = skillId;
        this.userId = userId;
        this.score = score;
    }

    public void updateScore(short newScore) {
        if (newScore < 1 || newScore > 5) throw new IllegalArgumentException("Score must be 1-5");
        this.score = newScore;
        this.updatedAt = LocalDateTime.now();
    }

    // getters
    public Long getId() { return id; }
    public Long getSkillId() { return skillId; }
    public String getUserId() { return userId; }
    public Short getScore() { return score; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
