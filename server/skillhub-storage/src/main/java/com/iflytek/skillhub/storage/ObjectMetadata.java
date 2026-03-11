package com.iflytek.skillhub.storage;

import java.time.Instant;

public record ObjectMetadata(long size, String contentType, Instant lastModified) {}
