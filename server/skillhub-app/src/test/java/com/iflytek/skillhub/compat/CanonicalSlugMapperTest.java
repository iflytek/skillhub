package com.iflytek.skillhub.compat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanonicalSlugMapperTest {

    private final CanonicalSlugMapper mapper = new CanonicalSlugMapper();

    @ParameterizedTest
    @CsvSource({
        "global,my-skill,my-skill",
        "team-ai,my-skill,team-ai--my-skill",
        "org-name,another-skill,org-name--another-skill"
    })
    void testToCanonical(String namespace, String slug, String expectedCanonical) {
        String result = mapper.toCanonical(namespace, slug);
        assertEquals(expectedCanonical, result);
    }

    @ParameterizedTest
    @CsvSource({
        "my-skill,global,my-skill",
        "team-ai--my-skill,team-ai,my-skill",
        "org-name--another-skill,org-name,another-skill"
    })
    void testFromCanonical(String canonical, String expectedNamespace, String expectedSlug) {
        SkillCoordinate result = mapper.fromCanonical(canonical);
        assertEquals(expectedNamespace, result.namespace());
        assertEquals(expectedSlug, result.slug());
    }

    @Test
    void testRoundTrip() {
        // Test global namespace
        String canonical1 = mapper.toCanonical("global", "my-skill");
        SkillCoordinate coord1 = mapper.fromCanonical(canonical1);
        assertEquals("global", coord1.namespace());
        assertEquals("my-skill", coord1.slug());

        // Test custom namespace
        String canonical2 = mapper.toCanonical("team-ai", "my-skill");
        SkillCoordinate coord2 = mapper.fromCanonical(canonical2);
        assertEquals("team-ai", coord2.namespace());
        assertEquals("my-skill", coord2.slug());
    }
}
