package com.iflytek.skillhub.domain.user;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateProfileResultTest {

    @Test
    void applied_createsAppliedResult() {
        UpdateProfileResult result = UpdateProfileResult.applied();
        assertThat(result).isInstanceOf(UpdateProfileResult.Applied.class);
    }

    @Test
    void pendingReview_createsPendingReviewResult() {
        UpdateProfileResult result = UpdateProfileResult.pendingReview();
        assertThat(result).isInstanceOf(UpdateProfileResult.PendingReview.class);
    }

    @Test
    void mixed_createsMixedResult() {
        UpdateProfileResult result = UpdateProfileResult.mixed(
                Map.of("name", "Alice"),
                Map.of("bio", "pending")
        );
        assertThat(result).isInstanceOf(UpdateProfileResult.Mixed.class);
        UpdateProfileResult.Mixed mixed = (UpdateProfileResult.Mixed) result;
        assertThat(mixed.appliedFields()).containsEntry("name", "Alice");
        assertThat(mixed.pendingFields()).containsEntry("bio", "pending");
    }
}
