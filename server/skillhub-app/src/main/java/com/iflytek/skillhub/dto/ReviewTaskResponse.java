package com.iflytek.skillhub.dto;

import java.time.Instant;

public record ReviewTaskResponse(
        Long id,
        Long skillVersionId,
        String namespace,
        String skillSlug,
        String version,
        String status,
        String submittedBy,
        String submittedByName,
        String reviewedBy,
        String reviewedByName,
        String reviewComment,
        Instant submittedAt,
        Instant reviewedAt,
        String subjectType,
        Long subjectId,
        Long subjectVersionId,
        String subjectSlug,
        Long avatarCandidateId,
        boolean avatarRemoval,
        String previousVisibility,
        String requestedVisibility,
        String originType,
        String displayName,
        String summary,
        String reviewKind,
        boolean canReview,
        String decisionMethod,
        String decisionScanTaskId,
        String autoPublicationGrantId
) {
    public ReviewTaskResponse(Long id, Long skillVersionId, String namespace, String skillSlug,
            String version, String status, String submittedBy, String submittedByName,
            String reviewedBy, String reviewedByName, String reviewComment, Instant submittedAt,
            Instant reviewedAt, String subjectType, Long subjectId, Long subjectVersionId,
            String subjectSlug, Long avatarCandidateId, boolean avatarRemoval,
            String previousVisibility, String requestedVisibility, String originType) {
        this(id, skillVersionId, namespace, skillSlug, version, status, submittedBy, submittedByName,
                reviewedBy, reviewedByName, reviewComment, submittedAt, reviewedAt, subjectType,
                subjectId, subjectVersionId, subjectSlug, avatarCandidateId, avatarRemoval,
                previousVisibility, requestedVisibility, originType, null, null, null, false,
                null, null, null);
    }

    public ReviewTaskResponse withReviewPermission(boolean allowed) {
        return new ReviewTaskResponse(id, skillVersionId, namespace, skillSlug, version, status,
                submittedBy, submittedByName, reviewedBy, reviewedByName, reviewComment,
                submittedAt, reviewedAt, subjectType, subjectId, subjectVersionId, subjectSlug,
                avatarCandidateId, avatarRemoval, previousVisibility, requestedVisibility,
                originType, displayName, summary, reviewKind, allowed,
                decisionMethod, decisionScanTaskId, autoPublicationGrantId);
    }

    public ReviewTaskResponse withPresentation(String displayName, String summary, String reviewKind) {
        return new ReviewTaskResponse(id, skillVersionId, namespace, skillSlug, version, status,
                submittedBy, submittedByName, reviewedBy, reviewedByName, reviewComment,
                submittedAt, reviewedAt, subjectType, subjectId, subjectVersionId, subjectSlug,
                avatarCandidateId, avatarRemoval, previousVisibility, requestedVisibility,
                originType, displayName, summary, reviewKind, canReview,
                decisionMethod, decisionScanTaskId, autoPublicationGrantId);
    }

    /**
     * Adds the structured decision basis. {@code reviewedBy} stays empty for a system decision, so
     * consumers must use {@code decisionMethod} rather than inferring a human from the submitter.
     */
    public ReviewTaskResponse withDecision(String method, String scanTaskId, String grantId) {
        return new ReviewTaskResponse(id, skillVersionId, namespace, skillSlug, version, status,
                submittedBy, submittedByName, reviewedBy, reviewedByName, reviewComment,
                submittedAt, reviewedAt, subjectType, subjectId, subjectVersionId, subjectSlug,
                avatarCandidateId, avatarRemoval, previousVisibility, requestedVisibility,
                originType, displayName, summary, reviewKind, canReview,
                method, scanTaskId, grantId);
    }

    public ReviewTaskResponse(Long id, Long skillVersionId, String namespace, String skillSlug, String version, String status, String submittedBy, String submittedByName, String reviewedBy, String reviewedByName, String reviewComment, Instant submittedAt, Instant reviewedAt, String subjectType, Long subjectId, Long subjectVersionId, String subjectSlug, String originType) {
        this(id, skillVersionId, namespace, skillSlug, version, status, submittedBy, submittedByName, reviewedBy, reviewedByName, reviewComment, submittedAt, reviewedAt, subjectType, subjectId, subjectVersionId, subjectSlug, null, false, null, null, originType);
    }

    public ReviewTaskResponse(Long id, Long skillVersionId, String namespace, String skillSlug, String version, String status, String submittedBy, String submittedByName, String reviewedBy, String reviewedByName, String reviewComment, Instant submittedAt, Instant reviewedAt, String subjectType, Long subjectId, Long subjectVersionId, String subjectSlug, Long avatarCandidateId, boolean avatarRemoval, String previousVisibility, String requestedVisibility) {
        this(id, skillVersionId, namespace, skillSlug, version, status, submittedBy, submittedByName, reviewedBy, reviewedByName, reviewComment, submittedAt, reviewedAt, subjectType, subjectId, subjectVersionId, subjectSlug, avatarCandidateId, avatarRemoval, previousVisibility, requestedVisibility, "USER");
    }

    public ReviewTaskResponse(Long id, Long skillVersionId, String namespace, String skillSlug, String version,
            String status, String submittedBy, String submittedByName, String reviewedBy, String reviewedByName,
            String reviewComment, Instant submittedAt, Instant reviewedAt, String subjectType, Long subjectId,
            Long subjectVersionId, String subjectSlug, Long avatarCandidateId, boolean avatarRemoval) {
        this(id, skillVersionId, namespace, skillSlug, version, status, submittedBy, submittedByName,
                reviewedBy, reviewedByName, reviewComment, submittedAt, reviewedAt, subjectType, subjectId,
                subjectVersionId, subjectSlug, avatarCandidateId, avatarRemoval, null, null, "USER");
    }
    public ReviewTaskResponse(Long id, Long skillVersionId, String namespace, String skillSlug, String version,
            String status, String submittedBy, String submittedByName, String reviewedBy, String reviewedByName,
            String reviewComment, Instant submittedAt, Instant reviewedAt, String subjectType, Long subjectId,
            Long subjectVersionId, String subjectSlug) {
        this(id, skillVersionId, namespace, skillSlug, version, status, submittedBy, submittedByName,
                reviewedBy, reviewedByName, reviewComment, submittedAt, reviewedAt, subjectType, subjectId,
                subjectVersionId, subjectSlug, null, false);
    }

    /** Backward-compatible constructor for Skill-only callers and tests. */
    public ReviewTaskResponse(
            Long id,
            Long skillVersionId,
            String namespace,
            String skillSlug,
            String version,
            String status,
            String submittedBy,
            String submittedByName,
            String reviewedBy,
            String reviewedByName,
            String reviewComment,
            Instant submittedAt,
            Instant reviewedAt
    ) {
        this(id, skillVersionId, namespace, skillSlug, version, status,
                submittedBy, submittedByName, reviewedBy, reviewedByName,
                reviewComment, submittedAt, reviewedAt,
                "SKILL_VERSION", null, skillVersionId, skillSlug, null, false);
    }
}
