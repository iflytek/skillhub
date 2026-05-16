package com.iflytek.skillhub.domain.bundle;

/**
 * Categorization of a skill bundle as described in the design doc.
 * <ul>
 *   <li>{@code PROJECT} — bound to one or more {@code targetProjectTypes}.</li>
 *   <li>{@code ROLE} — bound to one or more {@code roleTags}.</li>
 *   <li>{@code SCENARIO} — task-specific aggregation across project / role boundaries.</li>
 *   <li>{@code CUSTOM} — user-defined collection without a primary axis.</li>
 * </ul>
 */
public enum SkillBundleType {
    PROJECT,
    ROLE,
    SCENARIO,
    CUSTOM
}
