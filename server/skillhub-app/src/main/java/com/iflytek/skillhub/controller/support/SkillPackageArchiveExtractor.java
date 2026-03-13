package com.iflytek.skillhub.controller.support;

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

    public List<PackageEntry> extract(MultipartFile file) throws IOException {
        if (file.getSize() > SkillPackagePolicy.MAX_TOTAL_PACKAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Package too large: " + file.getSize() + " bytes (max: "
                            + SkillPackagePolicy.MAX_TOTAL_PACKAGE_SIZE + ")"
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

                if (entries.size() >= SkillPackagePolicy.MAX_FILE_COUNT) {
                    throw new IllegalArgumentException(
                            "Too many files: more than " + SkillPackagePolicy.MAX_FILE_COUNT
                    );
                }

                String normalizedPath = SkillPackagePolicy.normalizeEntryPath(zipEntry.getName());
                byte[] content = readEntry(zis, normalizedPath);
                totalSize += content.length;
                if (totalSize > SkillPackagePolicy.MAX_TOTAL_PACKAGE_SIZE) {
                    throw new IllegalArgumentException(
                            "Package too large: " + totalSize + " bytes (max: "
                                    + SkillPackagePolicy.MAX_TOTAL_PACKAGE_SIZE + ")"
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
            if (totalRead > SkillPackagePolicy.MAX_SINGLE_FILE_SIZE) {
                throw new IllegalArgumentException(
                        "File too large: " + path + " (" + totalRead + " bytes, max: "
                                + SkillPackagePolicy.MAX_SINGLE_FILE_SIZE + ")"
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
