package com.iflytek.skillhub.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalFileStorageService storageService;

    @BeforeEach
    void setUp() {
        StorageProperties props = new StorageProperties();
        props.getLocal().setBasePath(tempDir.toString());
        storageService = new LocalFileStorageService(props);
    }

    @Test
    void shouldPutAndGetObject() throws Exception {
        String key = "skills/1/1/SKILL.md";
        byte[] content = "# Hello".getBytes(StandardCharsets.UTF_8);
        storageService.putObject(key, new ByteArrayInputStream(content), content.length, "text/markdown");
        try (InputStream result = storageService.getObject(key)) {
            assertArrayEquals(content, result.readAllBytes());
        }
    }

    @Test
    void shouldCheckExistence() {
        assertFalse(storageService.exists("test/exists.txt"));
        byte[] content = "data".getBytes(StandardCharsets.UTF_8);
        storageService.putObject("test/exists.txt", new ByteArrayInputStream(content), content.length, "text/plain");
        assertTrue(storageService.exists("test/exists.txt"));
    }

    @Test
    void shouldDeleteObject() {
        byte[] content = "data".getBytes(StandardCharsets.UTF_8);
        storageService.putObject("test/delete.txt", new ByteArrayInputStream(content), content.length, "text/plain");
        assertTrue(storageService.exists("test/delete.txt"));
        storageService.deleteObject("test/delete.txt");
        assertFalse(storageService.exists("test/delete.txt"));
    }

    @Test
    void shouldDeleteMultipleObjects() {
        byte[] content = "data".getBytes(StandardCharsets.UTF_8);
        storageService.putObject("a/1.txt", new ByteArrayInputStream(content), content.length, "text/plain");
        storageService.putObject("a/2.txt", new ByteArrayInputStream(content), content.length, "text/plain");
        storageService.deleteObjects(List.of("a/1.txt", "a/2.txt"));
        assertFalse(storageService.exists("a/1.txt"));
        assertFalse(storageService.exists("a/2.txt"));
    }

    @Test
    void shouldGetMetadata() {
        byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);
        storageService.putObject("test/meta.txt", new ByteArrayInputStream(content), content.length, "text/plain");
        ObjectMetadata metadata = storageService.getMetadata("test/meta.txt");
        assertEquals(content.length, metadata.size());
        assertNotNull(metadata.lastModified());
    }

    @Test
    void shouldRejectPathTraversalKeys() {
        byte[] content = "data".getBytes(StandardCharsets.UTF_8);

        IllegalArgumentException putError = assertThrows(
                IllegalArgumentException.class,
                () -> storageService.putObject("../escape.txt", new ByteArrayInputStream(content), content.length, "text/plain")
        );
        assertEquals("Invalid storage key: ../escape.txt", putError.getMessage());

        IllegalArgumentException getError = assertThrows(
                IllegalArgumentException.class,
                () -> storageService.getObject("..\\escape.txt")
        );
        assertEquals("Invalid storage key: ..\\escape.txt", getError.getMessage());
    }

    @Test
    void generatePresignedUrlReturnsNullForLocalStorage() throws Exception {
        StorageProperties properties = new StorageProperties();
        properties.getLocal().setBasePath(tempDir.toString());
        Files.createDirectories(tempDir);

        LocalFileStorageService service = new LocalFileStorageService(properties);

        assertThat(service.generatePresignedUrl("packages/demo.zip", Duration.ofMinutes(10), "demo.zip")).isNull();
    }
}
