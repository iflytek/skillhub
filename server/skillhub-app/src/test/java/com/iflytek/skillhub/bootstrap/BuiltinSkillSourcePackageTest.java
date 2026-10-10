package com.iflytek.skillhub.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadata;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator;
import com.iflytek.skillhub.domain.skill.validation.ValidationResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BuiltinSkillSourcePackageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SkillMetadataParser metadataParser = new SkillMetadataParser();
    private final SkillPackageValidator packageValidator = new SkillPackageValidator(metadataParser);

    @Test
    void reviewedSourcesPassTheProductionPackageValidatorWithoutWarnings() throws IOException {
        Path repositoryRoot = locateRepositoryRoot();
        Path builtinRoot = repositoryRoot.resolve("builtin-skills");
        JsonNode catalog = objectMapper.readTree(builtinRoot.resolve("catalog.json").toFile());
        JsonNode skills = catalog.path("skills");

        assertThat(skills.isArray()).isTrue();

        Set<String> catalogSlugs = new HashSet<>();
        for (JsonNode item : skills) {
            String slug = item.path("slug").asText();
            String version = item.path("version").asText();
            String license = item.path("license").asText();
            Path skillDirectory = builtinRoot.resolve("skills").resolve(slug);

            assertThat(catalogSlugs.add(slug)).as("duplicate catalog slug %s", slug).isTrue();
            assertThat(skillDirectory).isDirectory();

            List<PackageEntry> entries = packageEntries(skillDirectory);
            ValidationResult validation = packageValidator.validate(entries);

            assertThat(validation.errors()).as("%s package errors", slug).isEmpty();
            assertThat(validation.warnings()).as("%s package warnings", slug).isEmpty();

            String skillMd = Files.readString(skillDirectory.resolve("SKILL.md"));
            SkillMetadata metadata = metadataParser.parse(skillMd);
            assertThat(metadata.name()).isEqualTo(slug);
            assertThat(metadata.version()).isEqualTo(version);
            assertThat(metadata.frontmatter().get("license")).isEqualTo(license);
        }

        try (var paths = Files.list(builtinRoot.resolve("skills"))) {
            Set<String> directorySlugs = paths
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .collect(java.util.stream.Collectors.toSet());
            assertThat(directorySlugs).isEqualTo(catalogSlugs);
        }
    }

    private static List<PackageEntry> packageEntries(Path skillDirectory) throws IOException {
        List<Path> files;
        try (var paths = Files.walk(skillDirectory)) {
            files = paths
                    .filter(path -> !path.equals(skillDirectory))
                    .peek(path -> assertThat(Files.isSymbolicLink(path))
                            .as("symbolic link %s", path)
                            .isFalse())
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> skillDirectory.relativize(path).toString()))
                    .toList();
        }

        List<PackageEntry> entries = new ArrayList<>();
        for (Path file : files) {
            byte[] content = Files.readAllBytes(file);
            String relativePath = skillDirectory.relativize(file).toString().replace('\\', '/');
            entries.add(new PackageEntry(relativePath, content, content.length, null));
        }
        return entries;
    }

    private static Path locateRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current.resolve("builtin-skills/skills"))
                    && Files.isDirectory(current.resolve("server/skillhub-app"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate SkillHub repository root");
    }
}
