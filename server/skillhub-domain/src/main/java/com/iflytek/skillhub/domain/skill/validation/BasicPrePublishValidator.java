package com.iflytek.skillhub.domain.skill.validation;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class BasicPrePublishValidator implements PrePublishValidator {

    private static final Pattern PLACEHOLDER_VALUE = Pattern.compile(
            "(?i).*(your|example|sample|placeholder|changeme|replace|dummy|mock|test|fake|todo|xxx|redacted).*"
    );
    private static final List<SecretRule> SECRET_RULES = List.of(
            new SecretRule(Pattern.compile("(AKIA[0-9A-Z]{16})"), 1, "cloud access key"),
            new SecretRule(Pattern.compile("(ghp_[A-Za-z0-9]{20,})"), 1, "GitHub token"),
            new SecretRule(Pattern.compile("(sk-[A-Za-z0-9]{20,})"), 1, "API key"),
            new SecretRule(
                    Pattern.compile("(?i)(api[_-]?key|access[_-]?key|secret|password|token)\\s*[:=]\\s*['\\\"]?([A-Za-z0-9_\\-]{12,})"),
                    2,
                    "secret or token")
    );

    @Override
    public ValidationResult validate(SkillPackageContext context) {
        List<String> errors = new ArrayList<>();

        for (PackageEntry entry : context.entries()) {
            if (!isTextLike(entry.path())) {
                continue;
            }
            String content = new String(entry.content(), StandardCharsets.UTF_8);
            String[] lines = content.split("\\R", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                for (SecretRule rule : SECRET_RULES) {
                    Matcher matcher = rule.pattern().matcher(line);
                    if (!matcher.find()) {
                        continue;
                    }
                    String matchedValue = matcher.group(rule.valueGroup());
                    if (isPlaceholderValue(matchedValue)) {
                        continue;
                    }
                    errors.add(entry.path()
                            + " line " + (i + 1)
                            + " contains a value that looks like a "
                            + rule.label()
                            + ". Replace real credentials with placeholders before publishing.");
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

    private boolean isPlaceholderValue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return PLACEHOLDER_VALUE.matcher(value).matches()
                || value.chars().allMatch(ch -> ch == 'x' || ch == 'X' || ch == '*' || ch == '-');
    }

    private record SecretRule(Pattern pattern, int valueGroup, String label) {}
}
