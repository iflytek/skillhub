package com.iflytek.skillhub.domain.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModerationResultTest {

    @Test
    void approved_createsApprovedResult() {
        ModerationResult result = ModerationResult.approved();
        assertThat(result.decision()).isEqualTo(ModerationDecision.APPROVED);
        assertThat(result.reason()).isNull();
    }

    @Test
    void rejected_createsRejectedResult() {
        ModerationResult result = ModerationResult.rejected("inappropriate");
        assertThat(result.decision()).isEqualTo(ModerationDecision.REJECTED);
        assertThat(result.reason()).isEqualTo("inappropriate");
    }

    @Test
    void needsReview_createsNeedsReviewResult() {
        ModerationResult result = ModerationResult.needsReview();
        assertThat(result.decision()).isEqualTo(ModerationDecision.NEEDS_REVIEW);
        assertThat(result.reason()).isNull();
    }
}
