package com.iflytek.skillhub.controller.support;

import com.iflytek.skillhub.config.SkillPublishProperties;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class SkillPackageArchiveExtractor {

    private final long maxTotalPackageSize;
    private final long maxSingleFileSize;
    private final int maxFileCount;

    public SkillPackageArchiveExtractor(SkillPublishProperties properties) {
        this.maxTotalPackageSize = properties.getMaxPackageSize();
        this.maxSingleFileSize = properties.getMaxSingleFileSize();
        this.maxFileCount = properties.getMaxFileCount();
    }

    public List<PackageEntry> extract(MultipartFile file) throws IOException {
        if (file.getSize() > maxTotalPackageSize) {
            throw new IllegalArgumentException(
                    "Package too large: " + file.getSize() + " bytes (max: "
                            + maxTotalPackageSize + ")"
            );
        }

        List<PackageEntry> entries = new ArrayList<>();
        long totalSize = 0;

        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                if (zipEntry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }

                if (entries.size() >= maxFileCount) {
                    throw new IllegalArgumentException(
                            "Too many files: more than " + maxFileCount
                    );
                }

                String normalizedPath = SkillPackagePolicy.normalizeEntryPath(zipEntry.getName());
                byte[] content = readEntry(zis, normalizedPath);
                totalSize += content.length;
                if (totalSize > maxTotalPackageSize) {
                    throw new IllegalArgumentException(
                            "Package too large: " + totalSize + " bytes (max: "
                                    + maxTotalPackageSize + ")"
                    );
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
        byte[] buffer = new byte[8192];
        long totalRead = 0;
        int read;
        while ((read = zis.read(buffer)) != -1) {
            totalRead += read;
            if (totalRead > maxSingleFileSize) {
                throw new IllegalArgumentException(
                        "File too large: " + path + " (" + totalRead + " bytes, max: "
                                + maxSingleFileSize + ")"
                );
            }
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
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
