package com.iflytek.skillhub.domain.suite.bundle;

import com.iflytek.skillhub.domain.skill.SkillVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/** Durable per-member plan and execution result for a confirmed Bundle operation. */
@Entity
@Table(name = "skill_suite_bundle_member_result")
public class SkillSuiteBundleMemberResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "operation_id", nullable = false, length = 64)
    private String operationId;
    @Column(name = "position", nullable = false)
    private int position;
    @Column(name = "namespace_slug", nullable = false, length = 128)
    private String namespaceSlug;
    @Column(name = "skill_slug", nullable = false, length = 128)
    private String skillSlug;
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private SkillSuiteBundleMemberSourceType sourceType;
    @Column(name = "package_path", length = 1024)
    private String packagePath;
    @Enumerated(EnumType.STRING)
    @Column(name = "requested_visibility", length = 32)
    private SkillVisibility requestedVisibility;
    @Column(name = "requested_version", length = 64)
    private String requestedVersion;
    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_change", nullable = false, length = 32)
    private SkillSuiteBundleRelationshipChange relationshipChange;
    @Enumerated(EnumType.STRING)
    @Column(name = "publish_action", nullable = false, length = 32)
    private SkillSuiteBundlePublishAction publishAction;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SkillSuiteBundleMemberResultStatus status;
    @Column(name = "fingerprint", length = 255)
    private String fingerprint;
    @Column(name = "skill_id")
    private Long skillId;
    @Column(name = "skill_version_id")
    private Long skillVersionId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "errors", nullable = false, columnDefinition = "jsonb")
    private List<String> errors;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warnings", nullable = false, columnDefinition = "jsonb")
    private List<String> warnings;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SkillSuiteBundleMemberResult() {
    }

    public SkillSuiteBundleMemberResult(
            String operationId, int position, SkillSuiteBundleCoordinate coordinate,
            SkillSuiteBundleMemberSourceType sourceType, String packagePath,
            SkillVisibility requestedVisibility, String requestedVersion,
            SkillSuiteBundleRelationshipChange relationshipChange,
            SkillSuiteBundlePublishAction publishAction, String fingerprint,
            Long skillId, Long skillVersionId, List<String> errors, List<String> warnings, Instant now
    ) {
        this.operationId = operationId;
        this.position = position;
        this.namespaceSlug = coordinate.namespace();
        this.skillSlug = coordinate.slug();
        this.sourceType = sourceType;
        this.packagePath = packagePath;
        this.requestedVisibility = requestedVisibility;
        this.requestedVersion = requestedVersion;
        this.relationshipChange = relationshipChange;
        this.publishAction = publishAction;
        this.fingerprint = fingerprint;
        this.skillId = skillId;
        this.skillVersionId = skillVersionId;
        this.errors = List.copyOf(errors);
        this.warnings = List.copyOf(warnings);
        this.status = SkillSuiteBundleMemberResultStatus.PLANNED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void cancelUnlessCompleted(Instant now) {
        if (status != SkillSuiteBundleMemberResultStatus.COMPLETED) {
            status = SkillSuiteBundleMemberResultStatus.CANCELLED;
            updatedAt = now;
        }
    }

    public boolean start(Instant now) {
        if (status != SkillSuiteBundleMemberResultStatus.PLANNED) {
            return false;
        }
        status = SkillSuiteBundleMemberResultStatus.RUNNING;
        updatedAt = now;
        return true;
    }

    public void bindVersion(Long resolvedSkillId, Long resolvedSkillVersionId, Instant now) {
        if (resolvedSkillId == null || resolvedSkillVersionId == null) {
            throw new IllegalArgumentException("Completed Bundle member requires Skill and version IDs");
        }
        skillId = resolvedSkillId;
        skillVersionId = resolvedSkillVersionId;
        updatedAt = now;
    }

    public void markWaiting(Instant now) {
        status = SkillSuiteBundleMemberResultStatus.WAITING_FOR_MEMBER;
        updatedAt = now;
    }

    public void markCompleted(Instant now) {
        if (skillId == null || skillVersionId == null) {
            throw new IllegalStateException("Bundle member cannot complete without bound IDs");
        }
        status = SkillSuiteBundleMemberResultStatus.COMPLETED;
        updatedAt = now;
    }

    public void markBlockedRetryable(String errorCode, Instant now) {
        status = SkillSuiteBundleMemberResultStatus.BLOCKED_RETRYABLE;
        errors = appendError(errorCode);
        updatedAt = now;
    }

    public void markRepreviewRequired(String errorCode, Instant now) {
        status = SkillSuiteBundleMemberResultStatus.REPREVIEW_REQUIRED;
        errors = appendError(errorCode);
        updatedAt = now;
    }

    public void retryUnlessCompleted(Instant now) {
        if (status == SkillSuiteBundleMemberResultStatus.BLOCKED_RETRYABLE) {
            status = SkillSuiteBundleMemberResultStatus.PLANNED;
            updatedAt = now;
        }
    }

    public void requireRepreviewUnlessCompleted(Instant now) {
        if (status != SkillSuiteBundleMemberResultStatus.COMPLETED) {
            status = SkillSuiteBundleMemberResultStatus.REPREVIEW_REQUIRED;
            updatedAt = now;
        }
    }

    private List<String> appendError(String errorCode) {
        if (errorCode == null || errorCode.isBlank() || errors.contains(errorCode)) {
            return errors;
        }
        java.util.ArrayList<String> updated = new java.util.ArrayList<>(errors);
        updated.add(errorCode);
        return List.copyOf(updated);
    }

    public Long getId() { return id; }
    public String getOperationId() { return operationId; }
    public int getPosition() { return position; }
    public String getNamespaceSlug() { return namespaceSlug; }
    public String getSkillSlug() { return skillSlug; }
    public SkillSuiteBundleMemberSourceType getSourceType() { return sourceType; }
    public String getPackagePath() { return packagePath; }
    public SkillVisibility getRequestedVisibility() { return requestedVisibility; }
    public String getRequestedVersion() { return requestedVersion; }
    public SkillSuiteBundleRelationshipChange getRelationshipChange() { return relationshipChange; }
    public SkillSuiteBundlePublishAction getPublishAction() { return publishAction; }
    public SkillSuiteBundleMemberResultStatus getStatus() { return status; }
    public String getFingerprint() { return fingerprint; }
    public Long getSkillId() { return skillId; }
    public Long getSkillVersionId() { return skillVersionId; }
    public List<String> getErrors() { return errors; }
    public List<String> getWarnings() { return warnings; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
