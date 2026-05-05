package com.iflytek.skillhub.domain.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SecurityScanRequestTest {

    @Test
    void record_shouldHoldValues() {
        SecurityScanRequest request = new SecurityScanRequest(
                "scan-1", 1L, "/path/to/skill", Map.of("key", "value"));

        assertThat(request.scanId()).isEqualTo("scan-1");
        assertThat(request.skillVersionId()).isEqualTo(1L);
        assertThat(request.skillPackagePath()).isEqualTo("/path/to/skill");
        assertThat(request.scanOptions()).containsEntry("key", "value");
    }
}
