package com.iflytek.skillhub.domain.skill.validation;

import java.util.List;

public record ValidationResult(
    boolean passed,
    List<String> errors,
    List<String> warnings
) {
    public static ValidationResult pass() {
        return new ValidationResult(true, List.of(), List.of());
    }

    public static ValidationResult fail(List<String> errors) {
        return new ValidationResult(false, List.copyOf(errors), List.of());
    }

    public static ValidationResult fail(String error) {
        return new ValidationResult(false, List.of(error), List.of());
    }

    public static ValidationResult warn(List<String> warnings) {
        return new ValidationResult(true, List.of(), List.copyOf(warnings));
    }

    public static ValidationResult of(List<String> errors, List<String> warnings) {
        List<String> safeErrors = errors == null ? List.of() : List.copyOf(errors);
        List<String> safeWarnings = warnings == null ? List.of() : List.copyOf(warnings);
        return new ValidationResult(safeErrors.isEmpty(), safeErrors, safeWarnings);
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
}
