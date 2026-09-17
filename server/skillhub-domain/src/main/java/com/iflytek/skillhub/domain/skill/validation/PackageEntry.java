package com.iflytek.skillhub.domain.skill.validation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;

/** A package file whose content can be reopened without retaining the whole package in memory. */
public final class PackageEntry {

    @FunctionalInterface
    public interface ContentSource {
        InputStream open() throws IOException;
    }

    private final String path;
    private final long size;
    private final String contentType;
    private final ContentSource contentSource;
    private final byte[] materializedContent;

    public PackageEntry(String path, byte[] content, long size, String contentType) {
        byte[] requiredContent = Objects.requireNonNull(content, "content");
        this.path = Objects.requireNonNull(path, "path");
        this.size = size;
        this.contentType = contentType;
        this.contentSource = () -> new ByteArrayInputStream(requiredContent);
        this.materializedContent = requiredContent;
    }

    private PackageEntry(String path, long size, String contentType, ContentSource contentSource) {
        this.path = Objects.requireNonNull(path, "path");
        this.size = size;
        this.contentType = contentType;
        this.contentSource = Objects.requireNonNull(contentSource, "contentSource");
        this.materializedContent = null;
    }

    public static PackageEntry streaming(
            String path, long size, String contentType, ContentSource contentSource
    ) {
        return new PackageEntry(path, size, contentType, contentSource);
    }

    public String path() {
        return path;
    }

    public long size() {
        return size;
    }

    public String contentType() {
        return contentType;
    }

    public InputStream openStream() throws IOException {
        return contentSource.open();
    }

    /**
     * Materializes one file for validators that inspect content. Whole-package processing should
     * prefer {@link #openStream()} so only the current file occupies heap memory.
     */
    public byte[] content() {
        if (materializedContent != null) {
            return materializedContent;
        }
        if (size < 0 || size >= Integer.MAX_VALUE) {
            throw new IllegalStateException("Invalid package entry size: " + path);
        }
        try (InputStream input = openStream()) {
            byte[] content = input.readNBytes((int) size + 1);
            if (content.length != size) {
                throw new IllegalStateException("Package entry size changed: " + path);
            }
            return content;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read package entry: " + path, exception);
        }
    }
}
