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
        Map<Integer, List<String>> versions = new LinkedHashMap<>();

        for (Path file : migrationFiles()) {
            Matcher matcher = VERSIONED_MIGRATION_PATTERN.matcher(file.getFileName().toString());
            if (!matcher.matches()) {
                continue;
            }
            int version = Integer.parseInt(matcher.group("version"));
            versions.computeIfAbsent(version, ignored -> new ArrayList<>())
                    .add(relativeToRepo(file));
        }

        List<String> duplicates = versions.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> "V" + entry.getKey() + " -> " + entry.getValue())
                .toList();

        assertThat(duplicates).isEmpty();
    }

    @Test
    void versionedMigrations_mustRemainContiguous() throws IOException {
        List<Integer> versions = migrationFiles().stream()
                .map(path -> VERSIONED_MIGRATION_PATTERN.matcher(path.getFileName().toString()))
                .filter(Matcher::matches)
                .map(matcher -> Integer.parseInt(matcher.group("version")))
                .sorted()
                .toList();

        List<String> gaps = new ArrayList<>();
        for (int expected = 1; expected <= versions.size(); expected++) {
            int actual = versions.get(expected - 1);
            if (actual != expected) {
                gaps.add("expected V" + expected + " but found V" + actual);
            }
        }

        assertThat(gaps).isEmpty();
    }

    @Test
    void migrationFiles_mustMatchFlywayVersionedNaming() throws IOException {
        List<String> invalidFiles = migrationFiles().stream()
                .map(path -> path.getFileName().toString())
                .filter(name -> !VERSIONED_MIGRATION_PATTERN.matcher(name).matches())
                .sorted()
                .toList();

        assertThat(invalidFiles).isEmpty();
    }

    private List<Path> migrationFiles() throws IOException {
        Path root = repoRoot()
                .resolve("server")
                .resolve("skillhub-app")
                .resolve("src/main/resources/db/migration");
        try (var stream = Files.list(root)) {
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
