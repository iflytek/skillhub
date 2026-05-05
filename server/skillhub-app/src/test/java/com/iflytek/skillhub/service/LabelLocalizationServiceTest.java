package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.domain.label.LabelTranslation;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

class LabelLocalizationServiceTest {

    private final LabelLocalizationService service = new LabelLocalizationService();

    @Test
    void resolveDisplayName_prefersFullLocaleMatch() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("zh-CN"));
        List<LabelTranslation> translations = List.of(
                new LabelTranslation(1L, "zh-CN", "Chinese Simplified"),
                new LabelTranslation(1L, "zh", "Chinese"),
                new LabelTranslation(1L, "en", "English")
        );

        String result = service.resolveDisplayName("slug", translations);

        assertThat(result).isEqualTo("Chinese Simplified");
    }

    @Test
    void resolveDisplayName_fallsBackToLanguageCode() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("zh-TW"));
        List<LabelTranslation> translations = List.of(
                new LabelTranslation(1L, "zh", "Chinese"),
                new LabelTranslation(1L, "en", "English")
        );

        String result = service.resolveDisplayName("slug", translations);

        assertThat(result).isEqualTo("Chinese");
    }

    @Test
    void resolveDisplayName_fallsBackToEnglish() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("fr-FR"));
        List<LabelTranslation> translations = List.of(
                new LabelTranslation(1L, "en", "English")
        );

        String result = service.resolveDisplayName("slug", translations);

        assertThat(result).isEqualTo("English");
    }

    @Test
    void resolveDisplayName_returnsSlugWhenNoMatch() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("de"));
        List<LabelTranslation> translations = List.of(
                new LabelTranslation(1L, "fr", "French")
        );

        String result = service.resolveDisplayName("my-slug", translations);

        assertThat(result).isEqualTo("my-slug");
    }

    @Test
    void resolveDisplayName_skipsBlankValues() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("en"));
        List<LabelTranslation> translations = List.of(
                new LabelTranslation(1L, "en", "  ")
        );

        String result = service.resolveDisplayName("fallback", translations);

        assertThat(result).isEqualTo("fallback");
    }

    @Test
    void resolveDisplayName_returnsSlugForEmptyTranslations() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        String result = service.resolveDisplayName("original", List.of());

        assertThat(result).isEqualTo("original");
    }

    @Test
    void resolveDisplayName_normalizesUnderscoreLocale() {
        LocaleContextHolder.setLocale(new Locale("zh", "CN"));
        List<LabelTranslation> translations = List.of(
                new LabelTranslation(1L, "zh-CN", "Match")
        );

        String result = service.resolveDisplayName("slug", translations);

        assertThat(result).isEqualTo("Match");
    }

    @Test
    void resolveDisplayName_usesFirstWhenDuplicateLocales() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        List<LabelTranslation> translations = List.of(
                new LabelTranslation(1L, "en", "First"),
                new LabelTranslation(1L, "en", "Second")
        );

        String result = service.resolveDisplayName("slug", translations);

        assertThat(result).isEqualTo("First");
    }

    @Test
    void resolveDisplayName_handlesNullLocale() {
        LocaleContextHolder.setLocale(new Locale("", ""));
        LabelTranslation mockTranslation = org.mockito.Mockito.mock(LabelTranslation.class);
        org.mockito.Mockito.when(mockTranslation.getLocale()).thenReturn(null);
        org.mockito.Mockito.when(mockTranslation.getDisplayName()).thenReturn("Fallback");

        String result = service.resolveDisplayName("slug", List.of(mockTranslation));

        assertThat(result).isEqualTo("Fallback");
    }
}
