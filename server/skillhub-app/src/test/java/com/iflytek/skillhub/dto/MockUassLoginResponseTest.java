package com.iflytek.skillhub.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockUassLoginResponseTest {

    @Test
    void recordInstantiation_createsResponse() {
        MockUassLoginResponse response = new MockUassLoginResponse("https://redirect");
        assertThat(response.redirectUrl()).isEqualTo("https://redirect");
    }
}
