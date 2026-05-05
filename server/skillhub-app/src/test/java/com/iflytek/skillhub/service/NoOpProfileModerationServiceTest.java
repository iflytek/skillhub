package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.user.ModerationResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpProfileModerationServiceTest {

    @Test
    void moderate_alwaysReturnsApproved() {
        NoOpProfileModerationService service = new NoOpProfileModerationService();

        ModerationResult result = service.moderate("user-1", Map.of("displayName", "Test"));

        assertThat(result.decision()).isEqualTo(com.iflytek.skillhub.domain.user.ModerationDecision.APPROVED);
    }
}
