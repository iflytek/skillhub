package com.iflytek.skillhub.domain.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "skill_version_stats")
public class SkillVersionStats {

    @Id
    @Column(name = "skill_version_id", nullable = false)
    private Long skillVersionId;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "download_count", nullable = false)
    private Long downloadCount = 0L;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SkillVersionStats() {
    }

    public SkillVersionStats(Long skillVersionId, Long skillId) {
        this.skillVersionId = skillVersionId;
        this.skillId = skillId;
    }

    @PrePersist
    protected void onCreate() {
        updatedAt = Instant.now(Clock.systemUTC());
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now(Clock.systemUTC());
    }

    public Long getSkillVersionId() {
        return skillVersionId;
    }

    public Long getSkillId() {
        return skillId;
    }

    public Long getDownloadCount() {
        return downloadCount;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
