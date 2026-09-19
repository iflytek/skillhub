package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.authoring.service.SkillDraftService;

/** Result of saving one draft file: updated draft plus file metadata. */
public record SaveDraftFileResponse(
        DraftResponse draft,
        DraftFileResponse file,
        boolean created,
        boolean revisionAdvanced
) {

    public static SaveDraftFileResponse from(SkillDraftService.SaveFileOutcome outcome) {
        return new SaveDraftFileResponse(
                DraftResponse.from(outcome.draft()),
                DraftFileResponse.from(outcome.file()),
                outcome.created(),
                outcome.revisionAdvanced());
    }
}
