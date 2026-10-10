package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.authoring.service.DraftSubmitService;

/** Outcome of submitting a validated draft: the published skill version coordinates. */
public record SubmitDraftResponse(
        Long skillId,
        Long versionId,
        String slug,
        String version
) {

    public static SubmitDraftResponse from(DraftSubmitService.SubmitOutcome outcome) {
        return new SubmitDraftResponse(
                outcome.skillId(), outcome.versionId(), outcome.slug(), outcome.version());
    }
}
