package com.iflytek.skillhub.dto;

/**
 * Status of a profile update operation, returned to the frontend.
 */
public enum ProfileUpdateStatus {
    /** Changes were applied immediately to user_account. */
    APPLIED,
    /** Changes are queued for human review (not yet applied). */
    PENDING_REVIEW
}
