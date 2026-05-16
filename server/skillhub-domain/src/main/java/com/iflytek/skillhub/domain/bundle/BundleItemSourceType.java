package com.iflytek.skillhub.domain.bundle;

/**
 * Where a skill inside a bundle came from.
 * <ul>
 *   <li>{@code REGISTRY} — references a published {@code skill_version} on the platform.</li>
 *   <li>{@code EMBEDDED} — uploaded as part of the bundle archive itself.</li>
 * </ul>
 */
public enum BundleItemSourceType {
    REGISTRY,
    EMBEDDED
}
