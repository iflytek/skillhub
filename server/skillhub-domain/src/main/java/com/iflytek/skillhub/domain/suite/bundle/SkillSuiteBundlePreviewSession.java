package com.iflytek.skillhub.domain.suite.bundle;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Expiring, non-reserving result of parsing and planning one uploaded Suite Bundle. */
@Entity
@Table(name = "skill_suite_bundle_preview")
public class SkillSuiteBundlePreviewSession {

    @Id
    @Column(name = "token", length = 64)
    private String token;
    @Column(name = "actor_id", nullable = false, length = 128)
    private String actorId;
    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 16)
    private SkillSuiteBundleMode mode;
    @Column(name = "namespace_id", nullable = false)
    private Long namespaceId;
    @Column(name = "target_suite_slug", nullable = false, length = 128)
    private String targetSuiteSlug;
    @Column(name = "target_suite_id")
    private Long targetSuiteId;
    @Column(name = "base_suite_version_id")
    private Long baseSuiteVersionId;
    @Column(name = "target_version", nullable = false, length = 64)
    private String targetVersion;
    @Column(name = "archive_object_key", nullable = false, length = 1024)
    private String archiveObjectKey;
    @Column(name = "archive_sha256", nullable = false, length = 64)
    private String archiveSha256;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "manifest_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> manifest;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "plan_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> plan;
    @Column(name = "warning_digest", nullable = false, length = 64)
    private String warningDigest;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SkillSuiteBundlePreviewStatus status;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "confirmed_at")
    private Instant confirmedAt;
    @Column(name = "staged_objects_cleaned_at")
    private Instant stagedObjectsCleanedAt;
    @Column(name = "staged_cleanup_failed_at")
    private Instant stagedCleanupFailedAt;
    @Column(name = "staged_cleanup_failure_code", length = 128)
    private String stagedCleanupFailureCode;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    protected SkillSuiteBundlePreviewSession() {
    }

    public SkillSuiteBundlePreviewSession(
            String token, String actorId, SkillSuiteBundleMode mode, Long namespaceId,
            String targetSuiteSlug, Long targetSuiteId, Long baseSuiteVersionId, String targetVersion,
            String archiveObjectKey, String archiveSha256, Map<String, Object> manifest,
            Map<String, Object> plan, String warningDigest, Instant expiresAt, Instant createdAt
    ) {
        this.token = token;
        this.actorId = actorId;
        this.mode = mode;
        this.namespaceId = namespaceId;
        this.targetSuiteSlug = targetSuiteSlug;
        this.targetSuiteId = targetSuiteId;
        this.baseSuiteVersionId = baseSuiteVersionId;
        this.targetVersion = targetVersion;
        this.archiveObjectKey = archiveObjectKey;
        this.archiveSha256 = archiveSha256;
        this.manifest = immutableJsonMap(manifest);
        this.plan = immutableJsonMap(plan);
        this.warningDigest = warningDigest;
        this.status = SkillSuiteBundlePreviewStatus.PREVIEW_READY;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public void markConfirmed(Instant confirmedAt) {
        this.status = SkillSuiteBundlePreviewStatus.CONFIRMED;
        this.confirmedAt = confirmedAt;
    }

    public void markExpired() {
        this.status = SkillSuiteBundlePreviewStatus.EXPIRED;
    }

    public void markStagedCleanupFailed(String failureCode, Instant now) {
        this.stagedCleanupFailureCode = failureCode;
        this.stagedCleanupFailedAt = now;
    }

    public void markStagedObjectsCleaned(Instant now) {
        this.stagedObjectsCleanedAt = now;
        this.stagedCleanupFailedAt = null;
        this.stagedCleanupFailureCode = null;
    }

    /**
     * Verifies immutable PreviewSession invariants before confirmation starts revalidating live state.
     */
    public void requireConfirmableBy(String actorId, String confirmedWarningDigest, Instant now) {
        if (!this.actorId.equals(actorId)) {
            throw new DomainForbiddenException("error.suite.bundle.preview.ownerMismatch");
        }
        if (status != SkillSuiteBundlePreviewStatus.PREVIEW_READY || !expiresAt.isAfter(now)) {
            throw new DomainBadRequestException("error.suite.bundle.preview.expired");
        }
        if (!warningDigest.equals(confirmedWarningDigest)) {
            throw new DomainBadRequestException("error.suite.bundle.preview.warningMismatch");
        }
    }

    public String getToken() { return token; }
    public String getActorId() { return actorId; }
    public SkillSuiteBundleMode getMode() { return mode; }
    public Long getNamespaceId() { return namespaceId; }
    public String getTargetSuiteSlug() { return targetSuiteSlug; }
    public Long getTargetSuiteId() { return targetSuiteId; }
    public Long getBaseSuiteVersionId() { return baseSuiteVersionId; }
    public String getTargetVersion() { return targetVersion; }
    public String getArchiveObjectKey() { return archiveObjectKey; }
    public String getArchiveSha256() { return archiveSha256; }
    public Map<String, Object> getManifest() { return manifest; }
    public Map<String, Object> getPlan() { return plan; }
    public String getWarningDigest() { return warningDigest; }
    public SkillSuiteBundlePreviewStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getStagedObjectsCleanedAt() { return stagedObjectsCleanedAt; }
    public Instant getStagedCleanupFailedAt() { return stagedCleanupFailedAt; }
    public String getStagedCleanupFailureCode() { return stagedCleanupFailureCode; }
    public Instant getCreatedAt() { return createdAt; }
    public long getLockVersion() { return lockVersion; }

    private Map<String, Object> immutableJsonMap(Map<String, Object> value) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
