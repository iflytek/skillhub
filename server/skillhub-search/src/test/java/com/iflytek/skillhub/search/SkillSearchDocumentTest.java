package com.iflytek.skillhub.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkillSearchDocumentTest {

    @Test
    void compactConstructor_shouldSetDefaults() {
        SkillSearchDocument doc = new SkillSearchDocument(
                1L, 2L, "ns", "owner-1", "Title", "Summary",
                "kw", "search", "vec", "PUBLIC", "ACTIVE");

        assertEquals(1L, doc.skillId());
        assertEquals(2L, doc.namespaceId());
        assertEquals("ns", doc.namespaceSlug());
        assertTrue(doc.labelSlugs().isEmpty());
        assertEquals(0L, doc.downloadCount());
        assertEquals(0D, doc.ratingAvg());
        assertEquals(0L, doc.updatedAtEpochMillis());
        assertEquals("ACTIVE", doc.namespaceStatus());
        assertFalse(doc.hidden());
    }

    @Test
    void fullConstructor_shouldPreserveAllFields() {
        SkillSearchDocument doc = new SkillSearchDocument(
                1L, 2L, "ns", "owner-1", "Title", "Summary",
                "kw", "search", "vec", "PUBLIC", "ACTIVE",
                List.of("ml"), 100L, 4.5D, 123456L, "ACTIVE", true);

        assertEquals(List.of("ml"), doc.labelSlugs());
        assertEquals(100L, doc.downloadCount());
        assertEquals(4.5D, doc.ratingAvg());
        assertEquals(123456L, doc.updatedAtEpochMillis());
        assertTrue(doc.hidden());
    }
}
