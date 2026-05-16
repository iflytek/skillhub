package com.iflytek.skillhub.domain.bundle;

/**
 * Severity-aware result of a single bundle validation check.
 * Aligns with {@code skill_bundle_validation_result.status}.
 */
public enum BundleValidationStatus {
    PASSED,
    WARNING,
    FAILED,
    SCANNING
}
