package com.iflytek.skillhub.domain.authoring.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * The raw outcome of one behavior validation task, consumed by the assertion
 * evaluator. Script tasks populate exit code and streams; prompt tasks populate
 * the agent response. The artifact resolver reads files produced inside the
 * working directory (used by artifact_exists assertions).
 */
public record TaskResult(
        Integer exitCode,
        String stdout,
        String stderr,
        String response,
        int toolCallCount,
        ArtifactResolver artifacts
) {

    /** Reads a file produced by the task, relative to the working directory. */
    public interface ArtifactResolver {
        Optional<byte[]> read(String relativePath);
    }

    public static TaskResult ofScript(int exitCode, String stdout, String stderr,
                                      int toolCallCount, ArtifactResolver artifacts) {
        return new TaskResult(exitCode, stdout, stderr, null, toolCallCount, artifacts);
    }

    public static TaskResult ofPrompt(String response, int toolCallCount, ArtifactResolver artifacts) {
        return new TaskResult(null, null, null, response, toolCallCount, artifacts);
    }

    public String sha256OfArtifact(String relativePath) {
        return artifacts.read(relativePath)
                .map(TaskResult::sha256Hex)
                .orElse(null);
    }

    public static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public static String sha256Hex(String content) {
        return sha256Hex(content.getBytes(StandardCharsets.UTF_8));
    }
}
