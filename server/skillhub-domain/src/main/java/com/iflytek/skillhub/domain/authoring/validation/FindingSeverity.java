package com.iflytek.skillhub.domain.authoring.validation;

/**
 * Severity of a validation finding. A run only fails when at least one ERROR finding exists.
 */
public enum FindingSeverity {
    ERROR,
    WARNING,
    INFO
}
