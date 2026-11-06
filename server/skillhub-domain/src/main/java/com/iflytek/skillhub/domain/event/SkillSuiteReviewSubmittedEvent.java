package com.iflytek.skillhub.domain.event;

/** A Suite version has entered review; consumers run only after its transaction commits. */
public record SkillSuiteReviewSubmittedEvent(
        Long reviewId,
        Long suiteId,
        Long versionId,
        String submitterId,
        Long namespaceId
) {
}
