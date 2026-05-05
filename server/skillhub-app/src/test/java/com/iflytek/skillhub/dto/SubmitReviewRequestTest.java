package com.iflytek.skillhub.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubmitReviewRequestTest {

    @Test
    void recordFieldsAreAccessible() {
        SubmitReviewRequest request = new SubmitReviewRequest("1.0.0", "PUBLIC");

        assertThat(request.version()).isEqualTo("1.0.0");
        assertThat(request.targetVisibility()).isEqualTo("PUBLIC");
    }
}
