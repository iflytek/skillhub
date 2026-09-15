package com.iflytek.skillhub.service.bundle;

import java.io.IOException;
import java.io.InputStream;

/** One normalized archive file already staged outside application memory. */
public record SkillSuiteBundleStagedEntry(
        String path,
        long size,
        String contentType,
        String sha256,
        String objectKey,
        InputStreamSupplier content
) {
    @FunctionalInterface
    public interface InputStreamSupplier {
        InputStream open() throws IOException;
    }
}
