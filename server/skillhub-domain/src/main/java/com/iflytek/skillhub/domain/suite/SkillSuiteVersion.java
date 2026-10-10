package com.iflytek.skillhub.domain.suite;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Clock;
import java.time.Instant;

/**
 * Immutable-after-publication snapshot of a Suite definition and its approved visibility.
 */
@Entity
@Table(name = "skill_suite_version")
public class SkillSuiteVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

    @Column(name = "suite_id", nullable = false)
    private Long suiteId;

    @Column(nullable = false, length = 64)
    private String version;

    @Column(name = "display_name", nullable = false, length = 256)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String overview;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SkillSuiteVersionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SkillVisibility visibility;

    @Column(columnDefinition = "TEXT")
    private String changelog;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "yanked_at")
    private Instant yankedAt;

    @Column(name = "yanked_by", length = 128)
    private String yankedBy;

    @Column(name = "yank_reason", columnDefinition = "TEXT")
    private String yankReason;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SkillSuiteVersion() {
    }

    public SkillSuiteVersion(Long suiteId, String version, SkillVisibility visibility, String createdBy) {
        this.suiteId = suiteId;
        this.version = version;
        this.displayName = version;
        this.visibility = visibility;
        this.createdBy = createdBy;
        this.status = SkillSuiteVersionStatus.DRAFT;
    }

    public SkillSuiteVersion(
            Long suiteId,
            String version,
            String displayName,
            String summary,
            SkillVisibility visibility,
            String createdBy
    ) {
        this(suiteId, version, visibility, createdBy);
        this.displayName = displayName;
        this.summary = summary;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now(Clock.systemUTC());
    }

    /**
     * Guards all definition changes. Review and published snapshots must not drift in place.
     */
    public void assertEditable() {
        if (status != SkillSuiteVersionStatus.DRAFT) {
            throw new DomainBadRequestException("error.suite.version.immutable", version);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getSuiteId() {
        return suiteId;
    }

    public String getVersion() {
        return version;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSummary() {
        return summary;
    }

    public String getOverview() {
        return overview;
    }

    public SkillSuiteVersionStatus getStatus() {
        return status;
    }

    public SkillVisibility getVisibility() {
        return visibility;
    }

    public String getChangelog() {
        return changelog;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getYankedAt() {
        return yankedAt;
    }

    public String getYankedBy() {
        return yankedBy;
    }

    public String getYankReason() {
        return yankReason;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setStatus(SkillSuiteVersionStatus status) {
        this.status = status;
    }

    public void setVisibility(SkillVisibility visibility) {
        assertEditable();
        this.visibility = visibility;
    }

    public void setDisplayName(String displayName) {
        assertEditable();
        this.displayName = displayName;
    }

    public void setSummary(String summary) {
        assertEditable();
        this.summary = summary;
    }

    public void setOverview(String overview) {
        assertEditable();
        this.overview = overview;
    }

    public void setChangelog(String changelog) {
        assertEditable();
        this.changelog = changelog;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public void setYankedAt(Instant yankedAt) {
        this.yankedAt = yankedAt;
    }

    public void setYankedBy(String yankedBy) {
        this.yankedBy = yankedBy;
    }

    public void setYankReason(String yankReason) {
        this.yankReason = yankReason;
    }
}
