package com.iflytek.skillhub.domain.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

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
    private LocalDateTime updatedAt;

    protected SkillVersionStats() {
    }

    public SkillVersionStats(Long skillVersionId, Long skillId) {
        this.skillVersionId = skillVersionId;
        this.skillId = skillId;
    }

    @PrePersist
    protected void onCreate() {
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
