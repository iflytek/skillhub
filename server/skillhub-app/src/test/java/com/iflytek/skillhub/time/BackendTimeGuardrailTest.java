package com.iflytek.skillhub.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class BackendTimeGuardrailTest {

    private static final Pattern LOCAL_DATE_TIME_NOW_PATTERN =
            Pattern.compile("\\bLocalDateTime\\s*\\.\\s*now\\s*\\(");

    private static final Pattern ENTITY_ANNOTATION_PATTERN =
            Pattern.compile("^\\s*@Entity\\b", Pattern.MULTILINE);

    private static final Pattern LOCAL_DATE_TIME_FIELD_PATTERN =
            Pattern.compile("^\\s*private\\s+LocalDateTime\\s+\\w+\\s*;", Pattern.MULTILINE);

    @Test
    void productionCode_mustNotIntroduceLocalDateTimeNow_calls() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path file : productionJavaFiles()) {
            String content = Files.readString(file);
            if (LOCAL_DATE_TIME_NOW_PATTERN.matcher(content).find()) {
                violations.add(relativeToRepo(file) + " uses LocalDateTime.now()");
            }
        }

        assertThat(violations).isEmpty();
    }

    @Test
    void entities_mustNotUseLocalDateTime_fields() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path file : productionJavaFiles()) {
            String content = Files.readString(file);
            if (!ENTITY_ANNOTATION_PATTERN.matcher(content).find()) {
                continue;
            }
            if (LOCAL_DATE_TIME_FIELD_PATTERN.matcher(content).find()) {
                violations.add(relativeToRepo(file) + " declares LocalDateTime field(s)");
            }
        }

        assertThat(violations).isEmpty();
    }

    private List<Path> productionJavaFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        for (String module : List.of("skillhub-app", "skillhub-auth", "skillhub-domain")) {
            Path root = repoRoot().resolve("server").resolve(module).resolve("src/main/java");
            try (var stream = Files.walk(root)) {
                stream.filter(path -> path.toString().endsWith(".java")).forEach(files::add);
            }
        }
        return files;
    }

    private Path repoRoot() {
        return Path.of("").toAbsolutePath().getParent().getParent();
    }

    private String relativeToRepo(Path file) {
        return repoRoot().relativize(file).toString();
    }
}
