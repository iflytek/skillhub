package com.iflytek.skillhub.domain.suite.bundle;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
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

/** Durable execution state created only after explicit Bundle confirmation. */
@Entity
@Table(name = "skill_suite_bundle_operation")
public class SkillSuiteBundleExecutionOperation {

    @Id
    @Column(name = "operation_id", length = 64)
    private String operationId;
    @Column(name = "preview_token", nullable = false, unique = true, length = 64)
    private String previewToken;
    @Column(name = "client_request_id", nullable = false, length = 64)
    private String clientRequestId;
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
    @Column(name = "reservation_key", nullable = false, length = 256)
    private String reservationKey;
    @Column(name = "reservation_active", nullable = false)
    private boolean reservationActive;
    @Column(name = "archive_object_key", nullable = false, length = 1024)
    private String archiveObjectKey;
    @Column(name = "archive_sha256", nullable = false, length = 64)
    private String archiveSha256;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "plan_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> plan;
    @Column(name = "warning_digest", nullable = false, length = 64)
    private String warningDigest;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SkillSuiteBundleOperationStatus status;
    @Column(name = "failure_code", length = 128)
    private String failureCode;
    @Column(name = "failure_detail")
    private String failureDetail;
    @Column(name = "result_suite_id")
    private Long resultSuiteId;
    @Column(name = "result_suite_version_id")
    private Long resultSuiteVersionId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    protected SkillSuiteBundleExecutionOperation() {
    }

    public SkillSuiteBundleExecutionOperation(
            String operationId, String previewToken, String clientRequestId, String actorId,
            SkillSuiteBundleMode mode, Long namespaceId, String targetSuiteSlug, Long targetSuiteId,
            Long baseSuiteVersionId, String targetVersion, String archiveObjectKey,
            String archiveSha256, Map<String, Object> plan, String warningDigest, Instant now
    ) {
        this.operationId = operationId;
        this.previewToken = previewToken;
        this.clientRequestId = clientRequestId;
        this.actorId = actorId;
        this.mode = mode;
        this.namespaceId = namespaceId;
        this.targetSuiteSlug = targetSuiteSlug;
        this.targetSuiteId = targetSuiteId;
        this.baseSuiteVersionId = baseSuiteVersionId;
        this.targetVersion = targetVersion;
        this.reservationKey = reservationKey(mode, namespaceId, targetSuiteSlug, targetSuiteId, targetVersion);
        this.reservationActive = true;
        this.archiveObjectKey = archiveObjectKey;
        this.archiveSha256 = archiveSha256;
        this.plan = Collections.unmodifiableMap(new LinkedHashMap<>(plan));
        this.warningDigest = warningDigest;
        this.status = SkillSuiteBundleOperationStatus.RUNNING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static String reservationKey(
            SkillSuiteBundleMode mode, Long namespaceId, String suiteSlug,
            Long suiteId, String targetVersion
    ) {
        if (mode == SkillSuiteBundleMode.CREATE) {
            if (namespaceId == null || suiteSlug == null || suiteSlug.isBlank()) {
                throw new IllegalArgumentException("CREATE reservation requires namespace and suite slug");
            }
            return "create:" + namespaceId + ":" + suiteSlug;
        }
        if (suiteId == null || targetVersion == null || targetVersion.isBlank()) {
            throw new IllegalArgumentException("UPDATE reservation requires suite and target version");
        }
        return "update:" + suiteId + ":" + targetVersion;
    }

    public void transition(SkillSuiteBundleOperationStatus next, Instant now) {
        this.status = next;
        this.updatedAt = now;
        if (next == SkillSuiteBundleOperationStatus.REPREVIEW_REQUIRED
                || next == SkillSuiteBundleOperationStatus.SUITE_DRAFT_CREATED
                || next == SkillSuiteBundleOperationStatus.CANCELLED) {
            this.reservationActive = false;
            this.completedAt = now;
        }
    }

    public boolean cancel(Instant now) {
        if (status == SkillSuiteBundleOperationStatus.CANCELLED) {
            return false;
        }
        if (status == SkillSuiteBundleOperationStatus.SUITE_DRAFT_CREATED
                || status == SkillSuiteBundleOperationStatus.REPREVIEW_REQUIRED) {
            throw new DomainBadRequestException("error.suite.bundle.operation.cancel.notAllowed");
        }
        transition(SkillSuiteBundleOperationStatus.CANCELLED, now);
        return true;
    }

    public void markBlockedRetryable(String code, String detail, Instant now) {
        this.failureCode = code;
        this.failureDetail = detail;
        transition(SkillSuiteBundleOperationStatus.BLOCKED_RETRYABLE, now);
    }

    public void retry(Instant now) {
        if (status != SkillSuiteBundleOperationStatus.BLOCKED_RETRYABLE) {
            throw new DomainBadRequestException("error.suite.bundle.operation.retry.notAllowed");
        }
        this.failureCode = null;
        this.failureDetail = null;
        transition(SkillSuiteBundleOperationStatus.RUNNING, now);
    }

    public void requireRetryable() {
        if (status != SkillSuiteBundleOperationStatus.BLOCKED_RETRYABLE) {
            throw new DomainBadRequestException("error.suite.bundle.operation.retry.notAllowed");
        }
    }

    public void markRepreviewRequired(String code, Instant now) {
        this.failureCode = code;
        this.failureDetail = null;
        transition(SkillSuiteBundleOperationStatus.REPREVIEW_REQUIRED, now);
    }

    public void markWaitingForMembers(Instant now) {
        this.failureCode = null;
        this.failureDetail = null;
        transition(SkillSuiteBundleOperationStatus.WAITING_FOR_MEMBERS, now);
    }

    public void markRunning(Instant now) {
        this.failureCode = null;
        this.failureDetail = null;
        transition(SkillSuiteBundleOperationStatus.RUNNING, now);
    }

    public void markSuiteDraftCreated(Long suiteId, Long suiteVersionId, Instant now) {
        if (suiteId == null || suiteVersionId == null) {
            throw new IllegalArgumentException("Bundle result requires Suite and version IDs");
        }
        this.resultSuiteId = suiteId;
        this.resultSuiteVersionId = suiteVersionId;
        this.failureCode = null;
        this.failureDetail = null;
        transition(SkillSuiteBundleOperationStatus.SUITE_DRAFT_CREATED, now);
    }

    public String getOperationId() { return operationId; }
    public String getPreviewToken() { return previewToken; }
    public String getClientRequestId() { return clientRequestId; }
    public String getActorId() { return actorId; }
    public SkillSuiteBundleMode getMode() { return mode; }
    public Long getNamespaceId() { return namespaceId; }
    public String getTargetSuiteSlug() { return targetSuiteSlug; }
    public Long getTargetSuiteId() { return targetSuiteId; }
    public Long getBaseSuiteVersionId() { return baseSuiteVersionId; }
    public String getTargetVersion() { return targetVersion; }
    public String getReservationKey() { return reservationKey; }
    public boolean isReservationActive() { return reservationActive; }
    public String getArchiveObjectKey() { return archiveObjectKey; }
    public String getArchiveSha256() { return archiveSha256; }
    public Map<String, Object> getPlan() { return plan; }
    public String getWarningDigest() { return warningDigest; }
    public SkillSuiteBundleOperationStatus getStatus() { return status; }
    public String getFailureCode() { return failureCode; }
    public String getFailureDetail() { return failureDetail; }
    public Long getResultSuiteId() { return resultSuiteId; }
    public Long getResultSuiteVersionId() { return resultSuiteVersionId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public long getLockVersion() { return lockVersion; }
}
