package com.iflytek.skillhub.domain.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ScannerTypeTest {

    @Test
    void fromValue_shouldReturnSkillScanner() {
        assertThat(ScannerType.fromValue("skill-scanner"))
                .isEqualTo(ScannerType.SKILL_SCANNER);
    }

    @Test
    void fromValue_shouldReturnCustom() {
        assertThat(ScannerType.fromValue("custom"))
                .isEqualTo(ScannerType.CUSTOM);
    }

    @Test
    void fromValue_shouldThrowForUnknownValue() {
        assertThatThrownBy(() -> ScannerType.fromValue("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown scanner type: unknown");
    }

    @Test
    void getValue_shouldReturnUnderlyingString() {
        assertThat(ScannerType.SKILL_SCANNER.getValue()).isEqualTo("skill-scanner");
        assertThat(ScannerType.CUSTOM.getValue()).isEqualTo("custom");
    }
}
