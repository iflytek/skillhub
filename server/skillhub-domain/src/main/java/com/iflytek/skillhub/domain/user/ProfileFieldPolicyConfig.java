package com.iflytek.skillhub.domain.user;

import java.util.Map;

/**
 * Domain-level abstraction for per-field profile edit policies.
 *
 * <p>Each field declares whether it is editable by the user and whether
 * changes require human review before taking effect.
 */
public interface ProfileFieldPolicyConfig {

    Map<String, FieldPolicy> fieldPolicies();

    record FieldPolicy(boolean editable, boolean requiresReview) {}
}
