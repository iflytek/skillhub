package com.iflytek.skillhub.service.authoring.adapter;

import com.iflytek.skillhub.domain.authoring.runtime.TaskResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads artifact files from a run's isolated working directory, rejecting paths that
 * would escape it. Shared by all runtime adapters.
 */
record WorkingDirectoryArtifacts(Path workingDirectory) implements TaskResult.ArtifactResolver {

    @Override
    public Optional<byte[]> read(String relativePath) {
        try {
            Path resolved = workingDirectory.resolve(relativePath).normalize();
            if (!resolved.startsWith(workingDirectory.normalize())) {
                return Optional.empty();
            }
            if (!Files.isRegularFile(resolved)) {
                return Optional.empty();
            }
            return Optional.of(Files.readAllBytes(resolved));
        } catch (IllegalArgumentException | IOException exception) {
            return Optional.empty();
        }
    }
}
