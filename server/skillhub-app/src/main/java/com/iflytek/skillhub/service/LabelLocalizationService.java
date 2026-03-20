package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.label.LabelTranslation;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

@Service
public class LabelLocalizationService {

    public String resolveDisplayName(String slug, List<LabelTranslation> translations) {
        Map<String, String> values = translations.stream()
                .collect(java.util.stream.Collectors.toMap(
                        translation -> normalizeLocale(translation.getLocale()),
                        LabelTranslation::getDisplayName,
                        (left, right) -> left
                ));
        Locale locale = LocaleContextHolder.getLocale();
        List<String> candidates = List.of(
                normalizeLocale(locale.toLanguageTag()),
                normalizeLocale(locale.getLanguage()),
                "en"
        );
        return candidates.stream()
                .map(values::get)
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse(slug);
    }

    private String normalizeLocale(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replace('_', '-').toLowerCase(Locale.ROOT);
    }
}
