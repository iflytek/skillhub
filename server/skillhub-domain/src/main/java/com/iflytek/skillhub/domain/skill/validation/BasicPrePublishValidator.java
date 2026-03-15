package com.iflytek.skillhub.domain.skill.validation;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class BasicPrePublishValidator implements PrePublishValidator {

    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile("AKIA[0-9A-Z]{16}"),
            Pattern.compile("ghp_[A-Za-z0-9]{20,}"),
            Pattern.compile("sk-[A-Za-z0-9]{20,}"),
            Pattern.compile("(?i)(api[_-]?key|access[_-]?key|secret|password|token)\\s*[:=]\\s*['\\\"]?[A-Za-z0-9_\\-]{12,}")
    );

    @Override
    public ValidationResult validate(SkillPackageContext context) {
        List<String> errors = new ArrayList<>();

        for (PackageEntry entry : context.entries()) {
            if (!isTextLike(entry.path())) {
                continue;
            }
            String content = new String(entry.content(), StandardCharsets.UTF_8);
            for (Pattern secretPattern : SECRET_PATTERNS) {
                if (secretPattern.matcher(content).find()) {
                    errors.add("Potential secret detected in " + entry.path());
                    break;
                }
            }
        }

        return errors.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(errors);
    }

    private boolean isTextLike(String path) {
        String lowerPath = path.toLowerCase(Locale.ROOT);
        return lowerPath.endsWith(".md")
                || lowerPath.endsWith(".txt")
                || lowerPath.endsWith(".json")
                || lowerPath.endsWith(".yaml")
                || lowerPath.endsWith(".yml")
                || lowerPath.endsWith(".js")
                || lowerPath.endsWith(".ts")
                || lowerPath.endsWith(".py")
                || lowerPath.endsWith(".sh")
                || lowerPath.endsWith(".svg");
    }
}
