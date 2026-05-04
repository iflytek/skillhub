package com.iflytek.skillhub.domain.social;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SkillStarTest {

    @Test
    void protectedConstructor_shouldBeAccessible() {
        SkillStar star = new SkillStar();
        assertNull(star.getId());
        assertNull(star.getSkillId());
        assertNull(star.getUserId());
        assertNull(star.getCreatedAt());
    }

    @Test
    void publicConstructor_shouldSetFields() {
        SkillStar star = new SkillStar(1L, "user-1");

        assertNull(star.getId());
        assertEquals(1L, star.getSkillId());
        assertEquals("user-1", star.getUserId());
        assertNull(star.getCreatedAt());
    }

    @Test
    void prePersist_shouldSetCreatedAt() {
        SkillStar star = new SkillStar(1L, "user-1");
        assertNull(star.getCreatedAt());

        star.prePersist();

        assertNotNull(star.getCreatedAt());
    }
}
