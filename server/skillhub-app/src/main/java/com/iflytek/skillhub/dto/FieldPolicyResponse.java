package com.iflytek.skillhub.dto;

/**
 * Per-field edit policy returned in the profile GET response.
 *
 * @param editable       whether the user can edit this field
 * @param requiresReview whether changes to this field require human review
 */
public record FieldPolicyResponse(boolean editable, boolean requiresReview) {}
