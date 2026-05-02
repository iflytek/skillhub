package com.iflytek.skillhub.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class FlywayMigrationGuardrailTest {

    private static final Pattern VERSIONED_MIGRATION_PATTERN =
            Pattern.compile("^V(?<version>\\d+)__(?<description>.+)\\.sql$");

    @Test
    void versionedMigrations_mustUseUniqueVersions() throws IOException {
        List<String> duplicates = new ArrayList<>();

        for (Path directory : migrationDirectories()) {
            Map<Integer, List<String>> versions = new LinkedHashMap<>();
            for (Path file : migrationFiles(directory)) {
                Matcher matcher = VERSIONED_MIGRATION_PATTERN.matcher(file.getFileName().toString());
                if (!matcher.matches()) {
                    continue;
                }
                int version = Integer.parseInt(matcher.group("version"));
                versions.computeIfAbsent(version, ignored -> new ArrayList<>())
                        .add(relativeToRepo(file));
            }
            duplicates.addAll(versions.entrySet().stream()
                    .filter(entry -> entry.getValue().size() > 1)
                    .map(entry -> relativeToRepo(directory) + ": V" + entry.getKey() + " -> " + entry.getValue())
                    .toList());
        }

        assertThat(duplicates).isEmpty();
    }

    @Test
    void versionedMigrations_mustRemainContiguous() throws IOException {
        List<String> gaps = new ArrayList<>();
        for (Path directory : migrationDirectories()) {
            List<Integer> versions = migrationFiles(directory).stream()
                    .map(path -> VERSIONED_MIGRATION_PATTERN.matcher(path.getFileName().toString()))
                    .filter(Matcher::matches)
                    .map(matcher -> Integer.parseInt(matcher.group("version")))
                    .sorted()
                    .toList();

            for (int expected = 1; expected <= versions.size(); expected++) {
                int actual = versions.get(expected - 1);
                if (actual != expected) {
                    gaps.add(relativeToRepo(directory) + ": expected V" + expected + " but found V" + actual);
                }
            }
        }

        assertThat(gaps).isEmpty();
    }

    @Test
    void migrationFiles_mustMatchFlywayVersionedNaming() throws IOException {
        List<String> invalidFiles = new ArrayList<>();
        for (Path directory : migrationDirectories()) {
            invalidFiles.addAll(migrationFiles(directory).stream()
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !VERSIONED_MIGRATION_PATTERN.matcher(name).matches())
                    .sorted()
                    .map(name -> relativeToRepo(directory) + "/" + name)
                    .toList());
        }

        assertThat(invalidFiles).isEmpty();
    }

    private List<Path> migrationDirectories() {
        return List.of(
                repoRoot()
                        .resolve("server")
                        .resolve("skillhub-app")
                        .resolve("src/main/resources/sql/migration"),
                repoRoot()
                        .resolve("server")
                        .resolve("skillhub-app")
                        .resolve("src/main/resources/sql/migration-mysql")
        );
    }

    private List<Path> migrationFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var stream = Files.list(directory)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private Path repoRoot() {
        return Path.of("").toAbsolutePath().getParent().getParent();
    }

    private String relativeToRepo(Path file) {
        return repoRoot().relativize(file).toString();
    }
}
