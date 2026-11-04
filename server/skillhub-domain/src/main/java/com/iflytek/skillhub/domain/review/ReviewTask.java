package com.iflytek.skillhub.domain.review;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_task")
public class ReviewTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_version_id")
    private Long skillVersionId;

    @Column(name = "skill_id")
    private Long skillId;

    @Column(name = "skill_version", length = 64)
    private String skillVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 32)
    private ReviewSubjectType subjectType;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin_type", nullable = false, length = 24)
    private ReviewOriginType originType = ReviewOriginType.USER;

    @Column(name = "github_sync_ingestion_id")
    private UUID githubSyncIngestionId;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "subject_version_id")
    private Long subjectVersionId;

    @Column(name = "subject_version", nullable = false, length = 64)
    private String subjectVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_visibility", length = 20)
    private com.iflytek.skillhub.domain.skill.SkillVisibility previousVisibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_visibility", length = 20)
    private com.iflytek.skillhub.domain.skill.SkillVisibility requestedVisibility;

    @Column(name = "visibility_scan_task_id", length = 100)
    private String visibilityScanTaskId;

    @Column(name = "visibility_version_id")
    private Long visibilityVersionId;

    @Column(name = "avatar_candidate_id")
    private Long avatarCandidateId;

    @Column(name = "avatar_removal_active_id")
    private Long avatarRemovalActiveId;
    @Column(name = "avatar_removal_pending_id")
    private Long avatarRemovalPendingId;

    @Column(name = "avatar_removal", nullable = false)
    private boolean avatarRemoval;

    @Column(name = "namespace_id", nullable = false)
    private Long namespaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewTaskStatus status = ReviewTaskStatus.PENDING;

    @Version
    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "submitted_by", nullable = false)
    private String submittedBy;

    @Column(name = "auto_approve_on_safe_scan", nullable = false)
    private boolean autoApproveOnSafeScan;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected ReviewTask() {}

    public ReviewTask(Long skillVersionId, Long namespaceId,
                      String submittedBy) {
        this.skillVersionId = skillVersionId;
        this.namespaceId = namespaceId;
        this.submittedBy = submittedBy;
    }

    public ReviewTask(Long skillVersionId, Long skillId, Long namespaceId,
                      String skillVersion, String submittedBy) {
        this.skillVersionId = skillVersionId;
        this.skillId = skillId;
        this.namespaceId = namespaceId;
        this.skillVersion = skillVersion;
        this.submittedBy = submittedBy;
        this.subjectType = ReviewSubjectType.SKILL_VERSION;
        this.subjectId = skillId;
        this.subjectVersionId = skillVersionId;
        this.subjectVersion = skillVersion;
    }

    /** Creates a typed Suite review without populating legacy Skill-specific columns. */
    public static ReviewTask forSuiteVersion(
            Long suiteVersionId,
            Long suiteId,
            Long namespaceId,
            String suiteVersion,
            String submittedBy
    ) {
        ReviewTask task = new ReviewTask();
        task.subjectType = ReviewSubjectType.SUITE_VERSION;
        task.subjectId = suiteId;
        task.subjectVersionId = suiteVersionId;
        task.subjectVersion = suiteVersion;
        task.namespaceId = namespaceId;
        task.submittedBy = submittedBy;
        return task;
    }

    public static ReviewTask forSkillMetadata(Long skillId, Long namespaceId, String actor, Long candidateId) {
        ReviewTask task = new ReviewTask();
        task.skillId = skillId;
        task.subjectType = ReviewSubjectType.SKILL_METADATA;
        task.subjectId = skillId;
        task.subjectVersion = "avatar:" + candidateId;
        task.namespaceId = namespaceId;
        task.submittedBy = actor;
        task.avatarCandidateId = candidateId;
        return task;
    }

    public static ReviewTask forVisibility(com.iflytek.skillhub.domain.skill.Skill skill,
            com.iflytek.skillhub.domain.skill.SkillVisibility target, String actor) {
        ReviewTask task = forSkillMetadata(skill.getId(), skill.getNamespaceId(), actor, null);
        task.subjectVersion = "visibility:" + java.util.UUID.randomUUID();
        task.previousVisibility = skill.getVisibility();
        task.requestedVisibility = target;
        task.visibilityVersionId = skill.getLatestVersionId();
        return task;
    }

    public com.iflytek.skillhub.domain.skill.SkillVisibility getPreviousVisibility() { return previousVisibility; }
    public com.iflytek.skillhub.domain.skill.SkillVisibility getRequestedVisibility() { return requestedVisibility; }
    public String getVisibilityScanTaskId() { return visibilityScanTaskId; }
    public void setVisibilityScanTaskId(String taskId) { visibilityScanTaskId = taskId; }
    public Long getVisibilityVersionId() { return visibilityVersionId; }

    public Long getAvatarCandidateId() { return avatarCandidateId; }
    public void setAvatarCandidateId(Long candidateId) { this.avatarCandidateId = candidateId; }
    public Long getAvatarRemovalActiveId() { return avatarRemovalActiveId; }
    public Long getAvatarRemovalPendingId() { return avatarRemovalPendingId; }
    public void prepareAvatarRemoval(Long active, Long pending) {
        avatarRemoval = true; avatarRemovalActiveId = active; avatarRemovalPendingId = pending;
    }
    public boolean isAvatarRemoval() { return avatarRemoval; }
    public void setAvatarRemoval(boolean removal) { this.avatarRemoval = removal; }
    public static ReviewTask forGithubSyncSkillVersion(Long skillVersionId, Long skillId,
                                                       Long namespaceId, String skillVersion,
                                                       String systemActorId, UUID ingestionId) {
        if (ingestionId == null) {
            throw new IllegalArgumentException("GitHub sync ingestion ID is required");
        }
        ReviewTask task = new ReviewTask(skillVersionId, skillId, namespaceId, skillVersion, systemActorId);
        task.originType = ReviewOriginType.GITHUB_SYNC;
        task.githubSyncIngestionId = ingestionId;
        return task;
    }


    public Long getId() { return id; }

    public Long getSkillVersionId() { return skillVersionId; }

    public Long getSkillId() { return skillId; }

    public String getSkillVersion() { return skillVersion; }

    public ReviewSubjectType getSubjectType() { return subjectType; }

    public ReviewOriginType getOriginType() { return originType; }

    public UUID getGithubSyncIngestionId() { return githubSyncIngestionId; }

    public Long getSubjectId() { return subjectId; }

    public Long getSubjectVersionId() { return subjectVersionId; }

    public String getSubjectVersion() { return subjectVersion; }

    public Long getNamespaceId() { return namespaceId; }

    public ReviewTaskStatus getStatus() { return status; }

    public void setStatus(ReviewTaskStatus status) { this.status = status; }

    public Integer getVersion() { return version; }

    public String getSubmittedBy() { return submittedBy; }

    public boolean isAutoApproveOnSafeScan() { return autoApproveOnSafeScan; }
    public void setAutoApproveOnSafeScan(boolean enabled) { this.autoApproveOnSafeScan = enabled; }
    public String getReviewedBy() { return reviewedBy; }

    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }

    public String getReviewComment() { return reviewComment; }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }

    public Instant getSubmittedAt() { return submittedAt; }

    public Instant getReviewedAt() { return reviewedAt; }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
