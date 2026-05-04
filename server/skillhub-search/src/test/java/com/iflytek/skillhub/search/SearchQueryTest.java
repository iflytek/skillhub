package com.iflytek.skillhub.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchQueryTest {

    @Test
    void compactConstructor_shouldDefaultLabelSlugsToEmptyList() {
        SearchQuery query = new SearchQuery("keyword", 1L,
                SearchVisibilityScope.anonymous(), "newest", 0, 10);
        assertEquals("keyword", query.keyword());
        assertEquals(1L, query.namespaceId());
        assertEquals("newest", query.sortBy());
        assertEquals(0, query.page());
        assertEquals(10, query.size());
        assertTrue(query.labelSlugs().isEmpty());
    }

    @Test
    void fullConstructor_shouldPreserveLabelSlugs() {
        SearchQuery query = new SearchQuery("keyword", 1L,
                SearchVisibilityScope.anonymous(), "newest", 0, 10, List.of("ml", "nlp"));
        assertEquals(List.of("ml", "nlp"), query.labelSlugs());
    }
}
