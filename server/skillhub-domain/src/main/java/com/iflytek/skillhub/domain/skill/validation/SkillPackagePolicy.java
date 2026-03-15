package com.iflytek.skillhub.domain.skill.validation;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public final class SkillPackagePolicy {

    public static final int MAX_FILE_COUNT = 100;
    public static final long MAX_SINGLE_FILE_SIZE = 1024 * 1024; // 1MB
    public static final long MAX_TOTAL_PACKAGE_SIZE = 10 * 1024 * 1024; // 10MB
    public static final String SKILL_MD_PATH = "SKILL.md";
    public static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".md", ".txt", ".json", ".yaml", ".yml",
            ".js", ".ts", ".py", ".sh",
            ".png", ".jpg", ".svg"
    );

    private SkillPackagePolicy() {
    }

    public static String normalizeEntryPath(String rawPath) {
        if (rawPath == null) {
            throw new IllegalArgumentException("Package entry path is missing");
        }

        String sanitized = rawPath.replace('\\', '/').trim();
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Package entry path is empty");
        }
        if (sanitized.startsWith("/") || sanitized.startsWith("\\")) {
            throw new IllegalArgumentException("Package entry path must be relative: " + rawPath);
        }
        if (sanitized.contains(":")) {
            throw new IllegalArgumentException("Package entry path contains an invalid drive or scheme prefix: " + rawPath);
        }

        Path normalized = Paths.get(sanitized).normalize();
        String canonical = normalized.toString().replace('\\', '/');
        if (normalized.isAbsolute() || canonical.isBlank()) {
            throw new IllegalArgumentException("Package entry path is invalid: " + rawPath);
        }
        if (canonical.equals(".") || canonical.equals("..") || canonical.startsWith("../")) {
            throw new IllegalArgumentException("Package entry path escapes package root: " + rawPath);
        }
        if (!sanitized.equals(canonical)) {
            throw new IllegalArgumentException("Package entry path must be normalized: " + rawPath);
        }

        return canonical;
    }

    public static boolean hasAllowedExtension(String path) {
        return ALLOWED_EXTENSIONS.stream().anyMatch(path::endsWith);
    }

    public static String validateContentMatchesExtension(String path, byte[] content) {
        String lowerPath = path.toLowerCase();
        if (lowerPath.endsWith(".png")) {
            return hasPrefix(content, (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
                    ? null
                    : "File content does not match extension: " + path;
        }
        if (lowerPath.endsWith(".jpg")) {
            return hasPrefix(content, (byte) 0xff, (byte) 0xd8, (byte) 0xff)
                    ? null
                    : "File content does not match extension: " + path;
        }
        if (lowerPath.endsWith(".svg")) {
            if (!isUtf8Text(content)) {
                return "File content does not match extension: " + path;
            }
            String text = new String(content, StandardCharsets.UTF_8).trim().toLowerCase();
            return text.contains("<svg") ? null : "File content does not match extension: " + path;
        }
        if (isTextExtension(lowerPath)) {
            return isUtf8Text(content) ? null : "File content does not match extension: " + path;
        }
        return null;
    }

    private static boolean isTextExtension(String path) {
        return path.endsWith(".md")
                || path.endsWith(".txt")
                || path.endsWith(".json")
                || path.endsWith(".yaml")
                || path.endsWith(".yml")
                || path.endsWith(".js")
                || path.endsWith(".ts")
                || path.endsWith(".py")
                || path.endsWith(".sh");
    }

    private static boolean isUtf8Text(byte[] content) {
        for (byte value : content) {
            if (value == 0) {
                return false;
            }
        }
        try {
            CharBuffer ignored = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
            return true;
        } catch (CharacterCodingException ex) {
            return false;
        }
    }

    private static boolean hasPrefix(byte[] content, int... prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if ((content[index] & 0xff) != (prefix[index] & 0xff)) {
                return false;
            }
        }
        return true;
    }
}
