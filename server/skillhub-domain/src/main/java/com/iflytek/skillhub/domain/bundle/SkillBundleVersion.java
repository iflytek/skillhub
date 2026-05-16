package com.iflytek.skillhub.domain.bundle;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A specific immutable release of a {@link SkillBundle}. Its {@code lockJson}
 * snapshots the per-skill coordinates so the bundle install plan is reproducible
 * even if upstream skills publish new versions.
 */
@Entity
@Table(name = "skill_bundle_version")
public class SkillBundleVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bundle_id", nullable = false)
    private Long bundleId;

    @Column(nullable = false, length = 32)
    private String version;

    @Column(name = "version_sort", nullable = false)
    private long versionSort;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SkillBundleVersionStatus status = SkillBundleVersionStatus.DRAFT;

    @Column(name = "manifest_json", nullable = false, columnDefinition = "jsonb")
    private String manifestJson;

    @Column(name = "lock_json", nullable = false, columnDefinition = "jsonb")
    private String lockJson;

    @Column(name = "bundle_storage_key", nullable = false, length = 512)
    private String bundleStorageKey;

    @Column(name = "file_count", nullable = false)
    private int fileCount;

    @Column(name = "total_size", nullable = false)
    private long totalSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false, length = 32)
    private BundleValidationStatus validationStatus = BundleValidationStatus.SCANNING;

    @Column(name = "reject_reason", length = 512)
    private String rejectReason;

    @Column(name = "published_by", length = 128)
    private String publishedBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected SkillBundleVersion() {}

    public SkillBundleVersion(Long bundleId, String version, long versionSort,
                              String manifestJson, String lockJson, String bundleStorageKey) {
        this.bundleId = bundleId;
        this.version = version;
        this.versionSort = versionSort;
        this.manifestJson = manifestJson;
        this.lockJson = lockJson;
        this.bundleStorageKey = bundleStorageKey;
    }

    public Long getId() { return id; }
    public Long getBundleId() { return bundleId; }
    public String getVersion() { return version; }
    public long getVersionSort() { return versionSort; }
    public SkillBundleVersionStatus getStatus() { return status; }
    public String getManifestJson() { return manifestJson; }
    public String getLockJson() { return lockJson; }
    public String getBundleStorageKey() { return bundleStorageKey; }
    public int getFileCount() { return fileCount; }
    public long getTotalSize() { return totalSize; }
    public BundleValidationStatus getValidationStatus() { return validationStatus; }
    public String getRejectReason() { return rejectReason; }
    public String getPublishedBy() { return publishedBy; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStatus(SkillBundleVersionStatus status) { this.status = status; }
    public void setValidationStatus(BundleValidationStatus validationStatus) { this.validationStatus = validationStatus; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public void setFileCount(int fileCount) { this.fileCount = fileCount; }
    public void setTotalSize(long totalSize) { this.totalSize = totalSize; }
}
