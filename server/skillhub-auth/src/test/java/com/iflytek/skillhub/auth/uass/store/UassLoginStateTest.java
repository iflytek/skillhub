package com.iflytek.skillhub.auth.uass.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class UassLoginStateTest {

    @Test
    void constructor_normalizesOptionalFieldsAndDefaultsProvider() {
        UassLoginState state = new UassLoginState(" /dashboard ", Instant.parse("2026-04-29T15:00:00Z"), " ", " fp ");

        assertThat(state.returnTo()).isEqualTo("/dashboard");
        assertThat(state.provider()).isEqualTo("uass");
        assertThat(state.requestFingerprint()).isEqualTo("fp");
    }

    @Test
    void constructor_rejectsBlankReturnToAndMissingTimestamp() {
        assertThatThrownBy(() -> new UassLoginState(" ", Instant.now(), "uass", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("returnTo must not be blank");
        assertThatThrownBy(() -> new UassLoginState("/dashboard", null, "uass", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("createdAt must not be null");
    }
}
