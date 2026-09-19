package com.iflytek.skillhub.domain.authoring.validation;

/**
 * Lifecycle of a fix suggestion attached to a finding.
 */
public enum FindingStatus {
    /** Suggestion is available but has not been applied to the draft. */
    OPEN,
    /** The suggestion's patch was applied and produced a new draft revision. */
    APPLIED,
    /** The user explicitly dismissed the suggestion. */
    DISMISSED
}
