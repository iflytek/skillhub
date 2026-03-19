package com.iflytek.skillhub.search;

import java.util.Set;

/**
 * Caller visibility context used by search implementations to filter results consistently.
 */
public record SearchVisibilityScope(
        String userId,
        Set<Long> memberNamespaceIds,
        Set<Long> adminNamespaceIds
) {
    public static SearchVisibilityScope anonymous() {
        return new SearchVisibilityScope(null, Set.of(), Set.of());
    }
}
