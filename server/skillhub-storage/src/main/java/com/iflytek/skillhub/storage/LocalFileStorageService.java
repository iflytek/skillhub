package com.iflytek.skillhub.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.List;

/**
 * Local-disk implementation of {@link ObjectStorageService}.
 *
 * <p>This adapter is intended for development or single-node deployments where
 * storing objects on the application filesystem is acceptable.
 */
@Service
@ConditionalOnProperty(name = "skillhub.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements ObjectStorageService {
    private final Path basePath;

    public LocalFileStorageService(StorageProperties properties) {
        this.basePath = Paths.get(properties.getLocal().getBasePath()).toAbsolutePath().normalize();
    }

    /**
     * Stores the incoming stream atomically by writing to a temporary sibling
     * file before replacing the final path.
     */
    @Override
    public void putObject(String key, InputStream data, long size, String contentType) {
        try {
            Path target = resolve(key);
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                data.transferTo(out);
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) { throw new StorageAccessException("putObject", key, e); }
    }

    /**
     * Opens the stored object as a streaming input.
     */
    @Override
    public InputStream getObject(String key) {
        try { return Files.newInputStream(resolve(key)); }
        catch (IOException e) { throw new StorageAccessException("getObject", key, e); }
    }

    /**
     * Resolves and deletes a single stored object if it exists.
     */
    @Override
    public void deleteObject(String key) {
        try { Files.deleteIfExists(resolve(key)); }
        catch (IOException e) { throw new StorageAccessException("deleteObject", key, e); }
    }

    @Override
    public void deleteObjects(List<String> keys) { keys.forEach(this::deleteObject); }

    @Override
    public boolean exists(String key) { return Files.exists(resolve(key)); }

    @Override
    public ObjectMetadata getMetadata(String key) {
        try {
            Path path = resolve(key);
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            return new ObjectMetadata(attrs.size(), Files.probeContentType(path), attrs.lastModifiedTime().toInstant());
        } catch (IOException e) { throw new StorageAccessException("getMetadata", key, e); }
    }

    @Override
    public String generatePresignedUrl(String key, Duration expiry, String downloadFilename) {
        return null;
    }

    private Path resolve(String key) {
        // Object keys use forward slashes; reject backslashes so traversal checks
        // behave consistently across platforms.
        if (key.contains("\\")) {
            throw new IllegalArgumentException("Invalid storage key: " + key);
        }
        Path resolved = basePath.resolve(key).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new IllegalArgumentException("Invalid storage key: " + key);
        }
        return resolved;
    }
}
