package com.iflytek.skillhub.domain.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PromotionRequestTest {

    @Test
    void protectedConstructor_shouldInitializeEmpty() {
        PromotionRequest request = new PromotionRequest();

        assertThat(request.getId()).isNull();
        assertThat(request.getSourceSkillId()).isNull();
        assertThat(request.getSourceVersionId()).isNull();
        assertThat(request.getTargetNamespaceId()).isNull();
        assertThat(request.getTargetSkillId()).isNull();
        assertThat(request.getStatus()).isEqualTo(ReviewTaskStatus.PENDING);
        assertThat(request.getVersion()).isEqualTo(1);
        assertThat(request.getSubmittedBy()).isNull();
        assertThat(request.getReviewedBy()).isNull();
        assertThat(request.getReviewComment()).isNull();
        assertThat(request.getSubmittedAt()).isNotNull();
        assertThat(request.getReviewedAt()).isNull();
    }

    @Test
    void publicConstructor_shouldSetFields() {
        PromotionRequest request = new PromotionRequest(1L, 2L, 3L, "user-1");

        assertThat(request.getId()).isNull();
        assertThat(request.getSourceSkillId()).isEqualTo(1L);
        assertThat(request.getSourceVersionId()).isEqualTo(2L);
        assertThat(request.getTargetNamespaceId()).isEqualTo(3L);
        assertThat(request.getSubmittedBy()).isEqualTo("user-1");
        assertThat(request.getStatus()).isEqualTo(ReviewTaskStatus.PENDING);
        assertThat(request.getVersion()).isEqualTo(1);
        assertThat(request.getSubmittedAt()).isNotNull();
    }

    @Test
    void setters_shouldUpdateFields() {
        PromotionRequest request = new PromotionRequest(1L, 2L, 3L, "user-1");

        request.setTargetSkillId(10L);
        request.setStatus(ReviewTaskStatus.APPROVED);
        request.setReviewedBy("reviewer-1");
        request.setReviewComment("LGTM");
        Instant reviewedAt = Instant.parse("2026-05-01T12:00:00Z");
        request.setReviewedAt(reviewedAt);

        assertThat(request.getTargetSkillId()).isEqualTo(10L);
        assertThat(request.getStatus()).isEqualTo(ReviewTaskStatus.APPROVED);
        assertThat(request.getReviewedBy()).isEqualTo("reviewer-1");
        assertThat(request.getReviewComment()).isEqualTo("LGTM");
        assertThat(request.getReviewedAt()).isEqualTo(reviewedAt);
    }

    @Test
    void getSubmittedAt_shouldReturnValue() {
        PromotionRequest request = new PromotionRequest(1L, 2L, 3L, "user-1");

        assertThat(request.getSubmittedAt()).isNotNull();
    }
}
