package com.iflytek.skillhub.domain.skill;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SkillTagTest {

    @Test
    void protectedConstructor_shouldBeAccessible() {
        SkillTag tag = new SkillTag();
        assertNull(tag.getId());
        assertNull(tag.getSkillId());
        assertNull(tag.getTagName());
        assertNull(tag.getVersionId());
        assertNull(tag.getCreatedBy());
        assertNull(tag.getCreatedAt());
        assertNull(tag.getUpdatedAt());
    }

    @Test
    void publicConstructor_shouldSetFields() {
        SkillTag tag = new SkillTag(1L, "v1.0.0", 2L, "user-1");

        assertNull(tag.getId());
        assertEquals(1L, tag.getSkillId());
        assertEquals("v1.0.0", tag.getTagName());
        assertEquals(2L, tag.getVersionId());
        assertEquals("user-1", tag.getCreatedBy());
    }

    @Test
    void onCreate_shouldSetTimestamps() {
        SkillTag tag = new SkillTag(1L, "v1.0.0", 2L, "user-1");
        assertNull(tag.getCreatedAt());
        assertNull(tag.getUpdatedAt());

        tag.onCreate();

        assertNotNull(tag.getCreatedAt());
        assertEquals(tag.getCreatedAt(), tag.getUpdatedAt());
    }

    @Test
    void onUpdate_shouldSetUpdatedAt() {
        SkillTag tag = new SkillTag(1L, "v1.0.0", 2L, "user-1");
        tag.onCreate();
        Instant beforeUpdate = tag.getUpdatedAt();

        try {
            Thread.sleep(5);
        } catch (InterruptedException ignored) {}
        tag.onUpdate();

        assertTrue(tag.getUpdatedAt().isAfter(beforeUpdate));
    }

    @Test
    void setVersionId_shouldUpdateVersionId() {
        SkillTag tag = new SkillTag(1L, "v1.0.0", 2L, "user-1");
        tag.setVersionId(3L);
        assertEquals(3L, tag.getVersionId());
    }
}
