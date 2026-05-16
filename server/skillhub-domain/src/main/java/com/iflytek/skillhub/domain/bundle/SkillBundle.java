package com.iflytek.skillhub.domain.bundle;

import com.iflytek.skillhub.domain.skill.SkillVisibility;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Aggregate root for a skill bundle (a curated collection of skills).
 *
 * <p>Distinct from {@link com.iflytek.skillhub.domain.skill.Skill}: bundles have their
 * own version stream, social signals, audit lifecycle, and downloads.
 */
@Entity
@Table(name = "skill_bundle")
public class SkillBundle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "namespace_id", nullable = false)
    private Long namespaceId;

    @Column(nullable = false, length = 128)
    private String slug;

    @Column(name = "display_name", nullable = false, length = 256)
    private String displayName;

    @Column(nullable = false, length = 512)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "bundle_type", nullable = false, length = 32)
    private SkillBundleType bundleType;

    @Column(name = "owner_id", nullable = false, length = 128)
    private String ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SkillVisibility visibility = SkillVisibility.NAMESPACE_ONLY;

    @Column(nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "latest_version_id")
    private Long latestVersionId;

    @Column(name = "download_count", nullable = false)
    private long downloadCount;

    @Column(name = "star_count", nullable = false)
    private int starCount;

    @Column(name = "rating_avg", precision = 3, scale = 2)
    private BigDecimal ratingAvg;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "updated_by", nullable = false, length = 128)
    private String updatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected SkillBundle() {}

    public SkillBundle(Long namespaceId, String slug, String displayName, String summary,
                       SkillBundleType bundleType, String ownerId, String createdBy) {
        this.namespaceId = namespaceId;
        this.slug = slug;
        this.displayName = displayName;
        this.summary = summary;
        this.bundleType = bundleType;
        this.ownerId = ownerId;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
    }

    public Long getId() { return id; }
    public Long getNamespaceId() { return namespaceId; }
    public String getSlug() { return slug; }
    public String getDisplayName() { return displayName; }
    public String getSummary() { return summary; }
    public SkillBundleType getBundleType() { return bundleType; }
    public String getOwnerId() { return ownerId; }
    public SkillVisibility getVisibility() { return visibility; }
    public String getStatus() { return status; }
    public Long getLatestVersionId() { return latestVersionId; }
    public long getDownloadCount() { return downloadCount; }
    public int getStarCount() { return starCount; }
    public BigDecimal getRatingAvg() { return ratingAvg; }
    public int getRatingCount() { return ratingCount; }
    public int getCommentCount() { return commentCount; }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setVisibility(SkillVisibility visibility) { this.visibility = visibility; }
    public void setStatus(String status) { this.status = status; }
    public void setLatestVersionId(Long latestVersionId) { this.latestVersionId = latestVersionId; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setBundleType(SkillBundleType bundleType) { this.bundleType = bundleType; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setStarCount(int starCount) { this.starCount = starCount; }
    public void setRatingCount(int ratingCount) { this.ratingCount = ratingCount; }
    public void setRatingAvg(BigDecimal ratingAvg) { this.ratingAvg = ratingAvg; }
    public void setCommentCount(int commentCount) { this.commentCount = commentCount; }
}
