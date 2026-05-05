package com.iflytek.skillhub.domain.skill.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpPrePublishValidatorTest {

    @Test
    void validate_alwaysPasses() {
        NoOpPrePublishValidator validator = new NoOpPrePublishValidator();
        ValidationResult result = validator.validate(null);
        assertThat(result.passed()).isTrue();
    }
}
