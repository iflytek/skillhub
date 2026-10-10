package com.iflytek.skillhub.search;

import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.JiebaSegmenter.SegMode;
import com.huaban.analysis.jieba.SegToken;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Normalizes mixed Chinese and ASCII search text into stable token sequences for
 * both indexing and querying while keeping the raw phrase available for
 * substring fallback matching.
 */
@Component
public class SearchTextTokenizer {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern ASCII_TOKEN = Pattern.compile("[\\p{ASCII}]+");

    private final JiebaSegmenter jiebaSegmenter = new JiebaSegmenter();

    public List<String> tokenizeForIndex(String text) {
        return tokenize(text, SegMode.INDEX);
    }

    public List<String> tokenizeForQuery(String text) {
        return tokenize(text, SegMode.SEARCH);
    }

    public String enrichForIndex(String rawText) {
        String normalized = normalize(rawText);
        if (normalized == null) {
            return "";
        }

        LinkedHashSet<String> parts = new LinkedHashSet<>();
        parts.add(normalized);
        parts.addAll(tokenizeForIndex(normalized));
        return String.join(" ", parts);
    }

    private List<String> tokenize(String text, SegMode mode) {
        String normalized = normalize(text);
        if (normalized == null) {
            return List.of();
        }

        Set<String> tokens = new LinkedHashSet<>();
        for (SegToken token : jiebaSegmenter.process(normalized, mode)) {
            String normalizedToken = normalizeToken(token.word);
            if (normalizedToken != null) {
                tokens.add(normalizedToken);
            }
        }
        return List.copyOf(tokens);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = WHITESPACE.matcher(value.trim()).replaceAll(" ");
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeToken(String token) {
        String normalized = normalize(token);
        if (normalized == null) {
            return null;
        }
        if (ASCII_TOKEN.matcher(normalized).matches()) {
            normalized = normalized.toLowerCase(Locale.ROOT);
        }
        return normalized;
    }
}
