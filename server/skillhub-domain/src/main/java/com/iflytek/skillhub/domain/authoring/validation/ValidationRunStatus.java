package com.iflytek.skillhub.domain.authoring.validation;

/**
 * Lifecycle of one validation run. Runs are immutable snapshots of a draft revision:
 * once started they execute the structure, configuration, and behavior layers in order
 * and settle into a terminal status.
 */
public enum ValidationRunStatus {
    QUEUED,
    PREPARING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    TIMED_OUT;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED || this == TIMED_OUT;
    }

    public boolean isActive() {
        return !isTerminal();
    }
}
