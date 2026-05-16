package com.iflytek.skillhub.service.media;

import com.iflytek.skillhub.domain.media.MediaAssetService;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Adapts {@link ObjectStorageService} to the domain port {@link MediaAssetService.MediaStorage}.
 * Buffers the entire body in memory because media uploads are bounded by validator limits
 * (10MB default), well below per-request budget.
 */
@Component
public class ObjectStorageMediaStorage implements MediaAssetService.MediaStorage {

    private final ObjectStorageService objectStorage;

    public ObjectStorageMediaStorage(ObjectStorageService objectStorage) {
        this.objectStorage = objectStorage;
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        objectStorage.putObject(key, new ByteArrayInputStream(bytes), bytes.length, contentType);
    }

    @Override
    public byte[] get(String key) {
        try (InputStream stream = objectStorage.getObject(key)) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read media object " + key, e);
        }
    }
}
