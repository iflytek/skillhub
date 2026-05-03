package com.iflytek.skillhub.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProfileChangeRequestTest {

    @Test
    void constructorAndPrePersistCaptureRequestPayload() {
        ProfileChangeRequest empty = new ProfileChangeRequest();

        assertThat(empty.getId()).isNull();
        assertThat(empty.getUserId()).isNull();
        assertThat(empty.getChanges()).isNull();
        assertThat(empty.getOldValues()).isNull();
        assertThat(empty.getStatus()).isEqualTo(ProfileChangeStatus.PENDING);
        assertThat(empty.getMachineResult()).isNull();
        assertThat(empty.getMachineReason()).isNull();
        assertThat(empty.getReviewerId()).isNull();
        assertThat(empty.getReviewComment()).isNull();
        assertThat(empty.getCreatedAt()).isNull();
        assertThat(empty.getReviewedAt()).isNull();

        ProfileChangeRequest request = new ProfileChangeRequest(
                "user-1",
                "{\"displayName\":\"new\"}",
                "{\"displayName\":\"old\"}",
                ProfileChangeStatus.APPROVED,
                "PASS",
                null
        );

        assertThat(request.getUserId()).isEqualTo("user-1");
        assertThat(request.getChanges()).isEqualTo("{\"displayName\":\"new\"}");
        assertThat(request.getOldValues()).isEqualTo("{\"displayName\":\"old\"}");
        assertThat(request.getStatus()).isEqualTo(ProfileChangeStatus.APPROVED);
        assertThat(request.getMachineResult()).isEqualTo("PASS");
        assertThat(request.getMachineReason()).isNull();

        request.prePersist();

        assertThat(request.getCreatedAt()).isNotNull();
    }

    @Test
    void settersUpdateReviewFields() {
        ProfileChangeRequest request = new ProfileChangeRequest(
                "user-1",
                "{\"displayName\":\"new\"}",
                "{\"displayName\":\"old\"}",
                ProfileChangeStatus.PENDING,
                "SKIPPED",
                "manual review required"
        );
        Instant reviewedAt = Instant.parse("2026-05-04T03:00:00Z");

        request.setStatus(ProfileChangeStatus.REJECTED);
        request.setReviewerId("reviewer-1");
        request.setReviewComment("needs changes");
        request.setReviewedAt(reviewedAt);

        assertThat(request.getStatus()).isEqualTo(ProfileChangeStatus.REJECTED);
        assertThat(request.getReviewerId()).isEqualTo("reviewer-1");
        assertThat(request.getReviewComment()).isEqualTo("needs changes");
        assertThat(request.getReviewedAt()).isEqualTo(reviewedAt);
        assertThat(request.getMachineResult()).isEqualTo("SKIPPED");
        assertThat(request.getMachineReason()).isEqualTo("manual review required");
    }
}
