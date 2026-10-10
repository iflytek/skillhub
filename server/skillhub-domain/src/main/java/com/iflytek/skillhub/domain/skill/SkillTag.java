package com.iflytek.skillhub.domain.skill;

import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "skill_tag")
public class SkillTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "tag_name", nullable = false, length = 50)
    private String tagName;

    @Column(name = "version_id")
    private Long versionId;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected SkillTag() {
    }

    public SkillTag(Long skillId, String tagName, Long versionId, String createdBy) {
        this.skillId = skillId;
        this.tagName = tagName;
        this.versionId = versionId;
        this.createdBy = createdBy;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now(Clock.systemUTC());
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now(Clock.systemUTC());
    }

    // Getters
    public Long getId() {
        return id;
    }

    public Long getSkillId() {
        return skillId;
    }

    public String getTagName() {
        return tagName;
    }

    public Long getVersionId() {
        return versionId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // Setter
    public void setVersionId(Long versionId) {
        this.versionId = versionId;
    }
}
