package com.iflytek.skillhub.search;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SearchVisibilityScopeTest {

    @Test
    void compactConstructor_shouldDefaultPlatformWideAccessToFalse() {
        SearchVisibilityScope scope = new SearchVisibilityScope("user-1", Set.of(1L), Set.of(2L));
        assertEquals("user-1", scope.userId());
        assertEquals(Set.of(1L), scope.memberNamespaceIds());
        assertEquals(Set.of(2L), scope.adminNamespaceIds());
        assertFalse(scope.platformWideAccess());
    }

    @Test
    void anonymous_shouldCreateScopeWithEmptySets() {
        SearchVisibilityScope scope = SearchVisibilityScope.anonymous();
        assertNull(scope.userId());
        assertTrue(scope.memberNamespaceIds().isEmpty());
        assertTrue(scope.adminNamespaceIds().isEmpty());
        assertFalse(scope.platformWideAccess());
    }

    @Test
    void fullConstructor_shouldPreservePlatformWideAccess() {
        SearchVisibilityScope scope = new SearchVisibilityScope("user-1", Set.of(), Set.of(), true);
        assertTrue(scope.platformWideAccess());
    }
}
