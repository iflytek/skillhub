package com.iflytek.skillhub.storage;

import java.time.Instant;

/**
 * Minimal metadata returned by object-storage providers for one stored object.
 */
public record ObjectMetadata(long size, String contentType, Instant lastModified) {}
