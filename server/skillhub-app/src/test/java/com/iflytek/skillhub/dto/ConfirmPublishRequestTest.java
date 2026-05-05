package com.iflytek.skillhub.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfirmPublishRequestTest {

    @Test
    void recordFieldsAreAccessible() {
        ConfirmPublishRequest request = new ConfirmPublishRequest("1.0.0");

        assertThat(request.version()).isEqualTo("1.0.0");
    }
}
