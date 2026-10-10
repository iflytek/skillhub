package com.iflytek.skillhub.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.namespace.SlugValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class BuiltinSkillManifestLoader {

    static final String MANIFEST_LOCATION = "classpath:builtin-skills/manifest.json";
    static final int MAX_ITEMS = 100;

    private static final Logger log = LoggerFactory.getLogger(BuiltinSkillManifestLoader.class);
    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    public BuiltinSkillManifestLoader(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    public List<ManifestItem> load() {
        Resource resource = resourceLoader.getResource(MANIFEST_LOCATION);
        if (!resource.exists()) {
            log.warn("Built-in skill manifest not found at {}", MANIFEST_LOCATION);
            return List.of();
        }

        JsonNode root;
        try (InputStream inputStream = resource.getInputStream()) {
            root = objectMapper.readTree(inputStream);
        } catch (IOException | RuntimeException ex) {
            log.warn("Failed to read built-in skill manifest at {}: {}", MANIFEST_LOCATION, ex.getMessage());
            return List.of();
        }

        if (root == null || root.isNull()) {
            log.warn("Built-in skill manifest at {} is empty", MANIFEST_LOCATION);
            return List.of();
        }

        JsonNode skillsNode = root.path("skills");
        if (!skillsNode.isArray()) {
            log.warn("Built-in skill manifest at {} does not contain an array field 'skills'", MANIFEST_LOCATION);
            return List.of();
        }

        List<ManifestItem> items = new ArrayList<>();
        Set<String> seenSlugVersions = new HashSet<>();
        int totalEntries = skillsNode.size();
        if (totalEntries > MAX_ITEMS) {
            log.warn("Built-in skill manifest has {} entries, only the first {} entries will be processed",
                    totalEntries, MAX_ITEMS);
        }
        int limit = Math.min(totalEntries, MAX_ITEMS);
        for (int index = 0; index < limit; index++) {
            JsonNode itemNode = skillsNode.get(index);
            String slug = text(itemNode, "slug");
            String version = text(itemNode, "version");
            String url = text(itemNode, "url");
            String sha256 = text(itemNode, "sha256");
            if (!StringUtils.hasText(slug)
                    || !StringUtils.hasText(version)
                    || !StringUtils.hasText(url)
                    || !StringUtils.hasText(sha256)) {
                log.warn(
                        "Skipping built-in skill manifest item {} because slug, version, url, and sha256 are required",
                        index
                );
                continue;
            }
            if (!SHA256_PATTERN.matcher(sha256).matches()) {
                log.warn(
                        "Skipping built-in skill manifest item {} because sha256 must be 64 lowercase hexadecimal characters",
                        index
                );
                continue;
            }
            try {
                SlugValidator.validate(slug);
            } catch (RuntimeException ex) {
                log.warn("Skipping built-in skill manifest item {} because slug is invalid [slug={}]: {}",
                        index, slug, ex.getMessage());
                continue;
            }

            String key = slug + "\n" + version;
            if (!seenSlugVersions.add(key)) {
                log.warn("Skipping duplicate built-in skill manifest item for slug={} version={}", slug, version);
                continue;
            }

            items.add(new ManifestItem(slug, version, url, sha256));
        }
        return List.copyOf(items);
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isTextual()) {
            return "";
        }
        return value.asText().trim();
    }

    public record ManifestItem(String slug, String version, String url, String sha256) {
    }
}
