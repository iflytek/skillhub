package com.iflytek.skillhub.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageServiceTest {

    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void generatePresignedUrl_returnsNullForLocalStorage() throws Exception {
        StorageProperties properties = new StorageProperties();
        properties.getLocal().setBasePath(tempDir.toString());
        Files.createDirectories(tempDir);

        LocalFileStorageService service = new LocalFileStorageService(properties);

        assertThat(service.generatePresignedUrl("packages/demo.zip", java.time.Duration.ofMinutes(10))).isNull();
    }
}
