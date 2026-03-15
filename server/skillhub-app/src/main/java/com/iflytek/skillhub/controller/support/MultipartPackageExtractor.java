package com.iflytek.skillhub.controller.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.SkillPublishProperties;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class MultipartPackageExtractor {

    private final SkillPublishProperties properties;
    private final ObjectMapper objectMapper;

    public MultipartPackageExtractor(SkillPublishProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public record PublishPayload(
        String slug,
        String displayName,
        String version,
        String changelog,
        Boolean acceptLicenseTerms,
        List<String> tags,
        ForkOf forkOf
    ) {
        public record ForkOf(String slug, String version) {}
    }

    public record ExtractedPackage(PublishPayload payload, List<PackageEntry> entries) {}

    public ExtractedPackage extract(MultipartFile[] files, String payloadJson) throws IOException {
        PublishPayload payload = objectMapper.readValue(payloadJson, PublishPayload.class);

        List<PackageEntry> entries = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();
        long totalSize = 0L;

        if (files != null) {
            for (MultipartFile file : files) {
                String originalFilename = file.getOriginalFilename();
                if (originalFilename == null || originalFilename.isBlank()) {
                    continue;
                }

                if (entries.size() >= properties.getMaxFileCount()) {
                    throw new DomainBadRequestException("error.skill.publish.package.invalid",
                            "Too many files: max " + properties.getMaxFileCount());
                }

                String normalizedPath = normalizePath(originalFilename);
                if (!seenPaths.add(normalizedPath)) {
                    throw new DomainBadRequestException("error.skill.publish.package.invalid",
                            "Duplicate package path: " + normalizedPath);
                }

                byte[] content = file.getBytes();
                totalSize += content.length;
                if (totalSize > properties.getMaxPackageSize()) {
                    throw new DomainBadRequestException("error.skill.publish.package.invalid",
                            "Package too large: max " + properties.getMaxPackageSize() + " bytes");
                }

                if (content.length > properties.getMaxSingleFileSize()) {
                    throw new DomainBadRequestException("error.skill.publish.package.invalid",
                            "File too large: " + normalizedPath + " (max " + properties.getMaxSingleFileSize() + " bytes)");
                }

                entries.add(new PackageEntry(
                        normalizedPath,
                        content,
                        content.length,
                        determineContentType(normalizedPath)
                ));
            }
        }

        return new ExtractedPackage(payload, entries);
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            throw new DomainBadRequestException("error.skill.publish.package.invalid", "Package entry path is blank");
        }
        if (path.contains("\\")) {
            path = path.replace("\\", "/");
        }

        // Remove leading ./
        while (path.startsWith("./")) {
            path = path.substring(2);
        }

        if (path.isBlank() || path.startsWith("../") || path.equals("..") || path.startsWith("/") || path.contains("//")) {
            throw new DomainBadRequestException("error.skill.publish.package.invalid",
                    "Unsafe package path: " + path);
        }
        return path;
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
