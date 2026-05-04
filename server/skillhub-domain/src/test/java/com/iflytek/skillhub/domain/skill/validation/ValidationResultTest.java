package com.iflytek.skillhub.domain.skill.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ValidationResultTest {

    @Test
    void passReturnsPassedResult() {
        ValidationResult result = ValidationResult.pass();

        assertThat(result.passed()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.hasWarnings()).isFalse();
    }

    @Test
    void failWithListReturnsFailedResult() {
        List<String> errors = List.of("error1", "error2");
        ValidationResult result = ValidationResult.fail(errors);

        assertThat(result.passed()).isFalse();
        assertThat(result.errors()).containsExactly("error1", "error2");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void failWithSingleErrorReturnsFailedResult() {
        ValidationResult result = ValidationResult.fail("single error");

        assertThat(result.passed()).isFalse();
        assertThat(result.errors()).containsExactly("single error");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void warnReturnsPassedResultWithWarnings() {
        List<String> warnings = List.of("warn1", "warn2");
        ValidationResult result = ValidationResult.warn(warnings);

        assertThat(result.passed()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).containsExactly("warn1", "warn2");
        assertThat(result.hasWarnings()).isTrue();
    }

    @Test
    void ofWithNullErrorsAndWarningsReturnsPassedResult() {
        ValidationResult result = ValidationResult.of(null, null);

        assertThat(result.passed()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void ofWithEmptyErrorsAndNonEmptyWarningsReturnsPassedResult() {
        ValidationResult result = ValidationResult.of(List.of(), List.of("warn1"));

        assertThat(result.passed()).isTrue();
        assertThat(result.hasWarnings()).isTrue();
    }

    @Test
    void ofWithNonEmptyErrorsReturnsFailedResult() {
        ValidationResult result = ValidationResult.of(List.of("error1"), List.of("warn1"));

        assertThat(result.passed()).isFalse();
        assertThat(result.errors()).containsExactly("error1");
        assertThat(result.warnings()).containsExactly("warn1");
    }
}
