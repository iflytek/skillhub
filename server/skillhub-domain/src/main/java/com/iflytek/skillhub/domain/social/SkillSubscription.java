package com.iflytek.skillhub.domain.social;

import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "skill_subscription",
    uniqueConstraints = @UniqueConstraint(columnNames = {"skill_id", "user_id"}))
public class SkillSubscription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SkillSubscription() {}

    public SkillSubscription(Long skillId, String userId) {
        this.skillId = skillId;
        this.userId = userId;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now(Clock.systemUTC());
    }

    public Long getId() { return id; }
    public Long getSkillId() { return skillId; }
    public String getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }
}
