package com.iflytek.skillhub.domain.review;

import jakarta.persistence.*;
import java.time.Instant;

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

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "subject_version_id")
    private Long subjectVersionId;

    @Column(name = "subject_version", nullable = false, length = 64)
    private String subjectVersion;

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

    public Long getId() { return id; }

    public Long getSkillVersionId() { return skillVersionId; }

    public Long getSkillId() { return skillId; }

    public String getSkillVersion() { return skillVersion; }

    public ReviewSubjectType getSubjectType() { return subjectType; }

    public Long getSubjectId() { return subjectId; }

    public Long getSubjectVersionId() { return subjectVersionId; }

    public String getSubjectVersion() { return subjectVersion; }

    public Long getNamespaceId() { return namespaceId; }

    public ReviewTaskStatus getStatus() { return status; }

    public void setStatus(ReviewTaskStatus status) { this.status = status; }

    public Integer getVersion() { return version; }

    public String getSubmittedBy() { return submittedBy; }

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
