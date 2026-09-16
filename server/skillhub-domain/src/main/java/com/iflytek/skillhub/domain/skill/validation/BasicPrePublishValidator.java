package com.iflytek.skillhub.domain.skill.validation;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default pre-publish validator that scans text-like package files for likely secrets and
 * accidental real credentials.
 */
@Component
public class BasicPrePublishValidator implements PrePublishValidator {

    private static final int MIN_GENERIC_SECRET_LENGTH = 12;
    private static final Pattern ASSIGNMENT_WITH_SENSITIVE_KEY = Pattern.compile(
            "(?i)(api[_-]?key|access[_-]?key|secret|password|token)\\s*[:=]\\s*"
    );
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern BARE_LITERAL = Pattern.compile("[A-Za-z0-9_\\-]{12,}");
    private static final Set<String> PLACEHOLDER_MARKERS = Set.of(
            "your", "example", "sample", "placeholder", "changeme", "replace", "dummy",
            "mock", "test", "fake", "todo", "xxx", "redacted");
    private static final List<SecretRule> SECRET_RULES = List.of(
            new SecretRule(Pattern.compile("(AKIA[0-9A-Z]{16})"), 1, "cloud access key"),
            new SecretRule(Pattern.compile("(ghp_[A-Za-z0-9]{20,})"), 1, "GitHub token"),
            new SecretRule(Pattern.compile("(sk-[A-Za-z0-9]{20,})"), 1, "API key"),
            new SecretRule(ASSIGNMENT_WITH_SENSITIVE_KEY, 0, "secret or token")
    );

    @Override
    public ValidationResult validate(SkillPackageContext context) {
        List<String> warnings = new ArrayList<>();

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
                    String matchedValue = extractMatchedValue(
                            line, matcher, rule, isBareSecretConfiguration(entry.path()));
                    if (matchedValue == null) {
                        continue;
                    }
                    if (isPlaceholderValue(matchedValue)) {
                        continue;
                    }
                    warnings.add(entry.path()
                            + " line " + (i + 1)
                            + " contains a value that looks like a "
                            + rule.label()
                            + ". Replace real credentials with placeholders before publishing.");
                    break;
                }
            }
        }

        return warnings.isEmpty() ? ValidationResult.pass() : ValidationResult.warn(warnings);
    }

    private boolean isTextLike(String path) {
        String lowerPath = path.toLowerCase(Locale.ROOT);
        return lowerPath.endsWith(".md") || lowerPath.endsWith(".txt")
                || lowerPath.endsWith(".json") || lowerPath.endsWith(".yaml") || lowerPath.endsWith(".yml")
                || lowerPath.endsWith(".js") || lowerPath.endsWith(".ts")
                || lowerPath.endsWith(".py") || lowerPath.endsWith(".sh") || lowerPath.endsWith(".svg")
                || lowerPath.endsWith(".html") || lowerPath.endsWith(".css") || lowerPath.endsWith(".csv")
                || lowerPath.endsWith(".toml") || lowerPath.endsWith(".xml") || lowerPath.endsWith(".ini")
                || lowerPath.endsWith(".cfg") || lowerPath.endsWith(".env")
                || lowerPath.endsWith(".rb") || lowerPath.endsWith(".go") || lowerPath.endsWith(".rs")
                || lowerPath.endsWith(".java") || lowerPath.endsWith(".kt") || lowerPath.endsWith(".lua")
                || lowerPath.endsWith(".sql") || lowerPath.endsWith(".r")
                || lowerPath.endsWith(".bat") || lowerPath.endsWith(".ps1")
                || lowerPath.endsWith(".zsh") || lowerPath.endsWith(".bash");
    }

    private boolean isBareSecretConfiguration(String path) {
        String lowerPath = path.toLowerCase(Locale.ROOT);
        return lowerPath.endsWith(".yaml") || lowerPath.endsWith(".yml")
                || lowerPath.endsWith(".toml") || lowerPath.endsWith(".ini")
                || lowerPath.endsWith(".cfg") || lowerPath.endsWith(".env");
    }

    private boolean isPlaceholderValue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalizedValue = value.toLowerCase(Locale.ROOT);
        return PLACEHOLDER_MARKERS.stream().anyMatch(normalizedValue::contains)
                || value.chars().allMatch(ch -> ch == 'x' || ch == 'X' || ch == '*' || ch == '-');
    }

    private String extractMatchedValue(
            String line, Matcher matcher, SecretRule rule, boolean allowBareLiteral) {
        if (rule.valueGroup() > 0) {
            return matcher.group(rule.valueGroup());
        }

        do {
            GenericValueScan scan = scanGenericValue(line, matcher.end(), allowBareLiteral);
            if (scan.literal() != null) {
                return scan.literal();
            }
            if (scan.nextSearchIndex() >= line.length()) {
                return null;
            }
            matcher.region(scan.nextSearchIndex(), line.length());
        } while (matcher.find());
        return null;
    }

    private GenericValueScan scanGenericValue(String line, int valueStart, boolean allowBareLiteral) {
        int start = valueStart;
        while (start < line.length() && Character.isWhitespace(line.charAt(start))) {
            start++;
        }
        if (start == line.length()) {
            return new GenericValueScan(null, line.length());
        }

        QuotedLiteralStart quotedStart = findQuotedLiteralStart(line, start);
        char first = line.charAt(quotedStart.index());
        if (first == '\'' || first == '"') {
            return scanQuotedLiteral(line, quotedStart.index(), first, quotedStart.wrapperDepth());
        }

        int end = start;
        while (end < line.length() && !isBareValueTerminator(line, end)) {
            end++;
        }
        String bareValue = line.substring(start, end);
        if (!allowBareLiteral && IDENTIFIER.matcher(bareValue).matches()) {
            return new GenericValueScan(null, end);
        }
        String literal = BARE_LITERAL.matcher(bareValue).matches() ? bareValue : null;
        return new GenericValueScan(literal, end);
    }

    private QuotedLiteralStart findQuotedLiteralStart(String line, int start) {
        int index = start;
        int wrapperDepth = 0;
        while (index < line.length() && line.charAt(index) == '(') {
            wrapperDepth++;
            index++;
            while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
                index++;
            }
        }
        return index < line.length() && (line.charAt(index) == '\'' || line.charAt(index) == '"')
                ? new QuotedLiteralStart(index, wrapperDepth)
                : new QuotedLiteralStart(start, 0);
    }

    private GenericValueScan scanQuotedLiteral(
            String line, int start, char quote, int wrapperDepth) {
        boolean escaped = false;
        for (int i = start + 1; i < line.length(); i++) {
            char current = line.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == quote) {
                String value = line.substring(start + 1, i);
                String literal = hasLiteralTerminator(line, i + 1, wrapperDepth)
                        && value.length() >= MIN_GENERIC_SECRET_LENGTH
                        ? value
                        : null;
                return new GenericValueScan(literal, i + 1);
            }
        }
        return new GenericValueScan(null, line.length());
    }

    private boolean hasLiteralTerminator(String line, int startIndex, int wrapperDepth) {
        int index = skipWhitespace(line, startIndex);
        for (int i = 0; i < wrapperDepth; i++) {
            if (index == line.length() || line.charAt(index) != ')') {
                return false;
            }
            index = skipWhitespace(line, index + 1);
        }
        if (isLiteralTerminatorAt(line, index)) {
            return true;
        }

        if (!line.startsWith("as", index)
                || index + 2 >= line.length()
                || !Character.isWhitespace(line.charAt(index + 2))) {
            return false;
        }
        index = skipWhitespace(line, index + 2);
        if (!line.startsWith("const", index)
                || (index + 5 < line.length()
                && Character.isJavaIdentifierPart(line.charAt(index + 5)))) {
            return false;
        }
        return isLiteralTerminatorAt(line, skipWhitespace(line, index + 5));
    }

    private int skipWhitespace(String line, int startIndex) {
        int index = startIndex;
        while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
            index++;
        }
        return index;
    }

    private boolean isLiteralTerminatorAt(String line, int index) {
        if (index == line.length()) {
            return true;
        }
        char current = line.charAt(index);
        return isTrailingDelimiter(current)
                || current == '#'
                || (current == '/' && index + 1 < line.length() && line.charAt(index + 1) == '/');
    }

    private boolean isTrailingDelimiter(char value) {
        return value == ',' || value == ';' || value == ')' || value == '}' || value == ']';
    }

    private boolean isBareValueTerminator(String line, int index) {
        char current = line.charAt(index);
        return Character.isWhitespace(current)
                || isTrailingDelimiter(current)
                || current == '#'
                || (current == '/' && index + 1 < line.length() && line.charAt(index + 1) == '/');
    }

    private record GenericValueScan(String literal, int nextSearchIndex) {}

    private record QuotedLiteralStart(int index, int wrapperDepth) {}

    private record SecretRule(Pattern pattern, int valueGroup, String label) {}
}
