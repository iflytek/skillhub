package com.iflytek.skillhub.domain.suite;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Clock;
import java.time.Instant;

/** Namespace-owned container whose published content is represented by immutable Suite versions. */
@Entity
@Table(name = "skill_suite")
public class SkillSuite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "namespace_id", nullable = false)
    private Long namespaceId;

    @Column(nullable = false, length = 128)
    private String slug;

    @Column(name = "display_name", nullable = false, length = 256)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SkillSuiteStatus status;

    @Column(name = "latest_version_id")
    private Long latestVersionId;

    @Column(name = "install_request_count", nullable = false)
    private Long installRequestCount = 0L;

    @Column(nullable = false)
    private boolean hidden;

    @Column(name = "hidden_at")
    private Instant hiddenAt;

    @Column(name = "hidden_by", length = 128)
    private String hiddenBy;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_by", length = 128)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SkillSuite() {
    }

    public SkillSuite(Long namespaceId, String slug, String displayName, String createdBy) {
        this.namespaceId = namespaceId;
        this.slug = slug;
        this.displayName = displayName;
        this.createdBy = createdBy;
        this.status = SkillSuiteStatus.ACTIVE;
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

    public Long getId() { return id; }
    public Long getNamespaceId() { return namespaceId; }
    public String getSlug() { return slug; }
    public String getDisplayName() { return displayName; }
    public String getSummary() { return summary; }
    public SkillSuiteStatus getStatus() { return status; }
    public Long getLatestVersionId() { return latestVersionId; }
    public Long getInstallRequestCount() { return installRequestCount; }
    public boolean isHidden() { return hidden; }
    public Instant getHiddenAt() { return hiddenAt; }
    public String getHiddenBy() { return hiddenBy; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setStatus(SkillSuiteStatus status) { this.status = status; }
    public void setLatestVersionId(Long latestVersionId) { this.latestVersionId = latestVersionId; }
    public void setInstallRequestCount(Long installRequestCount) { this.installRequestCount = installRequestCount; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }
    public void setHiddenAt(Instant hiddenAt) { this.hiddenAt = hiddenAt; }
    public void setHiddenBy(String hiddenBy) { this.hiddenBy = hiddenBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
