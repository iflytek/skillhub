package com.iflytek.skillhub.domain.skill.validation;

import java.util.List;

public record ValidationResult(
    boolean passed,
    List<String> errors
) {
    public static ValidationResult pass() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult fail(List<String> errors) {
        return new ValidationResult(false, errors);
    }

    public static ValidationResult fail(String error) {
        return new ValidationResult(false, List.of(error));
    }
}
