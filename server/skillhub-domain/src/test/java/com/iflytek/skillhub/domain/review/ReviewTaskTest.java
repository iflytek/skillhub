package com.iflytek.skillhub.domain.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReviewTaskTest {

    @Test
    void protectedConstructor_shouldInitializeEmpty() {
        ReviewTask task = new ReviewTask();

        assertThat(task.getId()).isNull();
        assertThat(task.getSkillVersionId()).isNull();
        assertThat(task.getNamespaceId()).isNull();
        assertThat(task.getStatus()).isEqualTo(ReviewTaskStatus.PENDING);
        assertThat(task.getVersion()).isEqualTo(1);
        assertThat(task.getSubmittedBy()).isNull();
        assertThat(task.getReviewedBy()).isNull();
        assertThat(task.getReviewComment()).isNull();
        assertThat(task.getSubmittedAt()).isNotNull();
        assertThat(task.getReviewedAt()).isNull();
    }

    @Test
    void publicConstructor_shouldSetFields() {
        ReviewTask task = new ReviewTask(1L, 2L, "user-1");

        assertThat(task.getId()).isNull();
        assertThat(task.getSkillVersionId()).isEqualTo(1L);
        assertThat(task.getNamespaceId()).isEqualTo(2L);
        assertThat(task.getSubmittedBy()).isEqualTo("user-1");
        assertThat(task.getStatus()).isEqualTo(ReviewTaskStatus.PENDING);
        assertThat(task.getVersion()).isEqualTo(1);
        assertThat(task.getSubmittedAt()).isNotNull();
    }

    @Test
    void setters_shouldUpdateFields() {
        ReviewTask task = new ReviewTask(1L, 2L, "user-1");

        task.setStatus(ReviewTaskStatus.APPROVED);
        task.setReviewedBy("reviewer-1");
        task.setReviewComment("LGTM");
        Instant reviewedAt = Instant.parse("2026-05-01T12:00:00Z");
        task.setReviewedAt(reviewedAt);

        assertThat(task.getStatus()).isEqualTo(ReviewTaskStatus.APPROVED);
        assertThat(task.getReviewedBy()).isEqualTo("reviewer-1");
        assertThat(task.getReviewComment()).isEqualTo("LGTM");
        assertThat(task.getReviewedAt()).isEqualTo(reviewedAt);
    }

    @Test
    void getSubmittedAt_shouldReturnValue() {
        ReviewTask task = new ReviewTask(1L, 2L, "user-1");

        assertThat(task.getSubmittedAt()).isNotNull();
    }
}
