package com.iflytek.skillhub.domain.authoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Clock;
import java.time.Instant;

/**
 * A skill authoring draft owned by one user. Drafts hold editable SKILL.md and resource files,
 * are content-addressed by {@code contentDigest}, and advance a monotonically increasing
 * {@code revision} on every content change. A draft is only submittable when its current
 * revision matches a revision with a passing validation run.
 */
@Entity
@Table(name = "skill_draft")
public class SkillDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "namespace_id", nullable = false)
    private Long namespaceId;

    @Column(name = "owner_id", nullable = false, length = 128)
    private String ownerId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String requirement;

    @Column(name = "revision", nullable = false)
    private Integer revision = 1;

    @Column(name = "content_digest", nullable = false, length = 64)
    private String contentDigest;

    @Column(name = "validated_revision")
    private Integer validatedRevision;

    @Column(name = "validated_run_id")
    private Long validatedRunId;

    @Column(name = "submitted_skill_id")
    private Long submittedSkillId;

    @Column(name = "submitted_version_id")
    private Long submittedVersionId;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long optimisticVersion;

    protected SkillDraft() {
    }

    public SkillDraft(Long namespaceId, String ownerId, String name, String requirement,
                      String contentDigest) {
        this.namespaceId = namespaceId;
        this.ownerId = ownerId;
        this.name = name;
        this.requirement = requirement;
        this.contentDigest = contentDigest;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now(Clock.systemUTC());
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now(Clock.systemUTC());
    }

    /**
     * Marks the draft content as changed: bumps the revision and attaches the new digest.
     * Validation state is intentionally preserved — callers compare
     * {@code validatedRevision} against {@code revision} to decide whether the draft
     * is still considered validated.
     */
    public void applyContentChange(int newRevision, String newContentDigest) {
        this.revision = newRevision;
        this.contentDigest = newContentDigest;
    }

    public void markValidated(int validatedRevision, Long validatedRunId) {
        this.validatedRevision = validatedRevision;
        this.validatedRunId = validatedRunId;
    }

    public void markSubmitted(Long skillId, Long versionId, Instant submittedAt) {
        this.submittedSkillId = skillId;
        this.submittedVersionId = versionId;
        this.submittedAt = submittedAt;
    }

    /** Returns true when the current revision has a passing validation run attached. */
    public boolean isCurrentRevisionValidated() {
        return validatedRevision != null && validatedRevision.equals(revision);
    }

    // Getters

    public Long getId() {
        return id;
    }

    public Long getNamespaceId() {
        return namespaceId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public String getRequirement() {
        return requirement;
    }

    public Integer getRevision() {
        return revision;
    }

    public String getContentDigest() {
        return contentDigest;
    }

    public Integer getValidatedRevision() {
        return validatedRevision;
    }

    public Long getValidatedRunId() {
        return validatedRunId;
    }

    public Long getSubmittedSkillId() {
        return submittedSkillId;
    }

    public Long getSubmittedVersionId() {
        return submittedVersionId;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getOptimisticVersion() {
        return optimisticVersion;
    }

    // Setters

    public void setName(String name) {
        this.name = name;
    }

    public void setRequirement(String requirement) {
        this.requirement = requirement;
    }
}
