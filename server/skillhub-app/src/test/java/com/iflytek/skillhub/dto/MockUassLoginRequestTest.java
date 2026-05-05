package com.iflytek.skillhub.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockUassLoginRequestTest {

    @Test
    void recordInstantiation_createsRequest() {
        MockUassLoginRequest request = new MockUassLoginRequest(
                "state-1", "https://cb", "uss-1", "Alice", "13800138000", "alice@example.com"
        );
        assertThat(request.state()).isEqualTo("state-1");
        assertThat(request.callbackUrl()).isEqualTo("https://cb");
        assertThat(request.ussId()).isEqualTo("uss-1");
        assertThat(request.displayName()).isEqualTo("Alice");
        assertThat(request.mobile()).isEqualTo("13800138000");
        assertThat(request.email()).isEqualTo("alice@example.com");
    }
}
