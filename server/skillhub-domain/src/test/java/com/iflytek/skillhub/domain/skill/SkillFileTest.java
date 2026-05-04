package com.iflytek.skillhub.domain.skill;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SkillFileTest {

    @Test
    void protectedConstructor_shouldBeAccessible() {
        SkillFile file = new SkillFile();
        assertNull(file.getId());
        assertNull(file.getVersionId());
        assertNull(file.getFilePath());
        assertNull(file.getFileSize());
        assertNull(file.getContentType());
        assertNull(file.getSha256());
        assertNull(file.getStorageKey());
        assertNull(file.getCreatedAt());
    }

    @Test
    void publicConstructor_shouldSetFields() {
        SkillFile file = new SkillFile(1L, "README.md", 1024L, "text/markdown", "abc123", "storage/key/1");

        assertNull(file.getId());
        assertEquals(1L, file.getVersionId());
        assertEquals("README.md", file.getFilePath());
        assertEquals(1024L, file.getFileSize());
        assertEquals("text/markdown", file.getContentType());
        assertEquals("abc123", file.getSha256());
        assertEquals("storage/key/1", file.getStorageKey());
        assertNull(file.getCreatedAt());
    }

    @Test
    void onCreate_shouldSetCreatedAt() {
        SkillFile file = new SkillFile(1L, "README.md", 1024L, "text/markdown", "abc123", "storage/key/1");
        assertNull(file.getCreatedAt());

        file.onCreate();

        assertNotNull(file.getCreatedAt());
    }
}
