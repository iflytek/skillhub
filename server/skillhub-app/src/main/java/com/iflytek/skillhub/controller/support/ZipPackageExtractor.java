package com.iflytek.skillhub.controller.support;

import com.iflytek.skillhub.config.SkillPublishProperties;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class ZipPackageExtractor {

    private static final int BUFFER_SIZE = 8192;

    private final SkillPublishProperties properties;

    public ZipPackageExtractor(SkillPublishProperties properties) {
        this.properties = properties;
    }

    public List<PackageEntry> extract(MultipartFile file) throws IOException {
        List<PackageEntry> entries = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();
        long totalSize = 0L;

        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                if (zipEntry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }

                if (entries.size() >= properties.getMaxFileCount()) {
                    throw new DomainBadRequestException("error.skill.publish.package.invalid",
                            "Too many files: max " + properties.getMaxFileCount());
                }

                String normalizedPath = normalizeEntryPath(zipEntry.getName());
                if (!seenPaths.add(normalizedPath)) {
                    throw new DomainBadRequestException("error.skill.publish.package.invalid",
                            "Duplicate package path: " + normalizedPath);
                }

                byte[] content = readEntry(zis, normalizedPath);
                totalSize += content.length;
                if (totalSize > properties.getMaxPackageSize()) {
                    throw new DomainBadRequestException("error.skill.publish.package.invalid",
                            "Package too large: max " + properties.getMaxPackageSize() + " bytes");
                }

                entries.add(new PackageEntry(
                        normalizedPath,
                        content,
                        content.length,
                        determineContentType(normalizedPath)
                ));
                zis.closeEntry();
            }
        }

        return entries;
    }

    private byte[] readEntry(ZipInputStream zis, String path) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        long fileSize = 0L;
        while ((read = zis.read(buffer)) != -1) {
            fileSize += read;
            if (fileSize > properties.getMaxSingleFileSize()) {
                throw new DomainBadRequestException("error.skill.publish.package.invalid",
                        "File too large: " + path + " (max " + properties.getMaxSingleFileSize() + " bytes)");
            }
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }

    private String normalizeEntryPath(String path) {
        if (path == null || path.isBlank()) {
            throw new DomainBadRequestException("error.skill.publish.package.invalid", "Package entry path is blank");
        }
        if (path.contains("\\")) {
            throw new DomainBadRequestException("error.skill.publish.package.invalid",
                    "Package entry must use '/' separators: " + path);
        }

        try {
            Path normalized = Path.of(path).normalize();
            String normalizedPath = normalized.toString().replace('\\', '/');
            if (normalized.isAbsolute()
                    || normalizedPath.isBlank()
                    || normalizedPath.startsWith("../")
                    || normalizedPath.equals("..")
                    || path.startsWith("/")
                    || path.contains("//")) {
                throw new DomainBadRequestException("error.skill.publish.package.invalid",
                        "Unsafe package path: " + path);
            }
            return normalizedPath;
        } catch (InvalidPathException ex) {
            throw new DomainBadRequestException("error.skill.publish.package.invalid",
                    "Invalid package path: " + path);
        }
    }

    private String determineContentType(String filename) {
        if (filename.endsWith(".py")) return "text/x-python";
        if (filename.endsWith(".json")) return "application/json";
        if (filename.endsWith(".yaml") || filename.endsWith(".yml")) return "application/x-yaml";
        if (filename.endsWith(".txt")) return "text/plain";
        if (filename.endsWith(".md")) return "text/markdown";
        return "application/octet-stream";
    }
}
