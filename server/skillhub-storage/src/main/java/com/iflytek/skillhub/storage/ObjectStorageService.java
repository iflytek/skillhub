package com.iflytek.skillhub.storage;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;

/**
 * Storage abstraction for binary skill assets and bundles regardless of the backing provider.
 */
public interface ObjectStorageService {
    void putObject(String key, InputStream data, long size, String contentType);
    InputStream getObject(String key);
    void deleteObject(String key);
    void deleteObjects(List<String> keys);
    boolean exists(String key);
    ObjectMetadata getMetadata(String key);
    String generatePresignedUrl(String key, Duration expiry, String downloadFilename);
}
