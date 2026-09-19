package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.authoring.SkillDraft;
import java.time.Instant;

/** Draft summary/aggregate exposed to the authoring UI. */
public record DraftResponse(
        Long id,
        Long namespaceId,
        String name,
        String requirement,
        Integer revision,
        String contentDigest,
        boolean validated,
        Integer validatedRevision,
        Long validatedRunId,
        Long submittedSkillId,
        Long submittedVersionId,
        Instant submittedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static DraftResponse from(SkillDraft draft) {
        return new DraftResponse(
                draft.getId(),
                draft.getNamespaceId(),
                draft.getName(),
                draft.getRequirement(),
                draft.getRevision(),
                draft.getContentDigest(),
                draft.isCurrentRevisionValidated(),
                draft.getValidatedRevision(),
                draft.getValidatedRunId(),
                draft.getSubmittedSkillId(),
                draft.getSubmittedVersionId(),
                draft.getSubmittedAt(),
                draft.getCreatedAt(),
                draft.getUpdatedAt());
    }
}
