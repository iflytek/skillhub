package com.iflytek.skillhub.domain.skill;

import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "skill_version")
public class SkillVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(nullable = false, length = 64)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SkillVersionStatus status;

    @Column(columnDefinition = "TEXT")
    private String changelog;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_metadata_json", columnDefinition = "jsonb")
    private String parsedMetadataJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "manifest_json", columnDefinition = "jsonb")
    private String manifestJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_visibility", length = 20)
    private SkillVisibility requestedVisibility;

    @Column(name = "file_count", nullable = false)
    private Integer fileCount = 0;

    @Column(name = "total_size", nullable = false)
    private Long totalSize = 0L;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "bundle_ready", nullable = false)
    private boolean bundleReady;

    @Column(name = "download_ready", nullable = false)
    private boolean downloadReady;

    @Column(name = "yanked_at")
    private Instant yankedAt;

    @Column(name = "yanked_by", length = 128)
    private String yankedBy;

    @Column(name = "yank_reason", columnDefinition = "TEXT")
    private String yankReason;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SkillVersion() {
    }

    public SkillVersion(Long skillId, String version, String createdBy) {
        this.skillId = skillId;
        this.version = version;
        this.createdBy = createdBy;
        this.status = SkillVersionStatus.DRAFT;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now(Clock.systemUTC());
    }

    // Getters
    public Long getId() {
        return id;
    }

    public Long getSkillId() {
        return skillId;
    }

    public String getVersion() {
        return version;
    }

    public SkillVersionStatus getStatus() {
        return status;
    }

    public String getChangelog() {
        return changelog;
    }

    public String getParsedMetadataJson() {
        return parsedMetadataJson;
    }

    public String getManifestJson() {
        return manifestJson;
    }

    public SkillVisibility getRequestedVisibility() {
        return requestedVisibility;
    }

    public Integer getFileCount() {
        return fileCount;
    }

    public Long getTotalSize() {
        return totalSize;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public boolean isBundleReady() {
        return bundleReady;
    }

    public boolean isDownloadReady() {
        return downloadReady;
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

    // Setters
    public void setStatus(SkillVersionStatus status) {
        this.status = status;
    }

    public void setChangelog(String changelog) {
        this.changelog = changelog;
    }

    public void setParsedMetadataJson(String parsedMetadataJson) {
        this.parsedMetadataJson = parsedMetadataJson;
    }

    public void setManifestJson(String manifestJson) {
        this.manifestJson = manifestJson;
    }

    public void setRequestedVisibility(SkillVisibility requestedVisibility) {
        this.requestedVisibility = requestedVisibility;
    }

    public void setFileCount(Integer fileCount) {
        this.fileCount = fileCount;
    }

    public void setTotalSize(Long totalSize) {
        this.totalSize = totalSize;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public void setBundleReady(boolean bundleReady) {
        this.bundleReady = bundleReady;
    }

    public void setDownloadReady(boolean downloadReady) {
        this.downloadReady = downloadReady;
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
