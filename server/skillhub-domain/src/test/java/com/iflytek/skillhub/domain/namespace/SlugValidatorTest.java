package com.iflytek.skillhub.domain.namespace;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SlugValidatorTest {
    @Test
    void shouldAcceptValidSlug() {
        assertDoesNotThrow(() -> SlugValidator.validate("my-namespace"));
        assertDoesNotThrow(() -> SlugValidator.validate("ab"));
        assertDoesNotThrow(() -> SlugValidator.validate("test123"));
        assertDoesNotThrow(() -> SlugValidator.validate("my-team-2024"));
    }
    @Test
    void shouldRejectTooShort() {
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () -> SlugValidator.validate("a"));
        assertEquals("error.slug.length", ex.messageCode());
    }
    @Test
    void shouldRejectTooLong() {
        String longSlug = "a".repeat(65);
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () -> SlugValidator.validate(longSlug));
        assertEquals("error.slug.length", ex.messageCode());
    }
    @Test
    void shouldRejectUpperCase() {
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () -> SlugValidator.validate("MyNamespace"));
        assertEquals("error.slug.pattern", ex.messageCode());
    }
    @Test
    void shouldRejectStartingWithHyphen() {
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () -> SlugValidator.validate("-namespace"));
        assertEquals("error.slug.pattern", ex.messageCode());
    }
    @Test
    void shouldRejectEndingWithHyphen() {
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () -> SlugValidator.validate("namespace-"));
        assertEquals("error.slug.pattern", ex.messageCode());
    }
    @Test
    void shouldRejectDoubleHyphen() {
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () -> SlugValidator.validate("my--namespace"));
        assertEquals("error.slug.doubleHyphen", ex.messageCode());
    }
    @Test
    void shouldRejectReservedWords() {
        assertThrows(DomainBadRequestException.class, () -> SlugValidator.validate("admin"));
        assertThrows(DomainBadRequestException.class, () -> SlugValidator.validate("api"));
        assertThrows(DomainBadRequestException.class, () -> SlugValidator.validate("global"));
        assertThrows(DomainBadRequestException.class, () -> SlugValidator.validate("system"));
    }
    @Test
    void shouldRejectUnderscores() {
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () -> SlugValidator.validate("my_namespace"));
        assertEquals("error.slug.pattern", ex.messageCode());
    }

    @Test
    void shouldAcceptChineseSlug() {
        assertDoesNotThrow(() -> SlugValidator.validate("胡炎淋技能"));
        assertDoesNotThrow(() -> SlugValidator.validate("技能包"));
    }

    @Test
    void shouldAcceptEmojiSlug() {
        assertDoesNotThrow(() -> SlugValidator.validate("🚀-rocket"));
        assertDoesNotThrow(() -> SlugValidator.validate("🎯-target"));
    }

    @Test
    void shouldAcceptMixedUnicodeSlug() {
        assertDoesNotThrow(() -> SlugValidator.validate("my-技能-v2"));
        assertDoesNotThrow(() -> SlugValidator.validate("skill-包-2024"));
        assertDoesNotThrow(() -> SlugValidator.validate("测试-test-123"));
    }

    @Test
    void shouldAcceptJapaneseSlug() {
        assertDoesNotThrow(() -> SlugValidator.validate("スキル"));
        assertDoesNotThrow(() -> SlugValidator.validate("テスト-skill"));
    }

    @Test
    void shouldAcceptKoreanSlug() {
        assertDoesNotThrow(() -> SlugValidator.validate("스킬"));
        assertDoesNotThrow(() -> SlugValidator.validate("테스트-skill"));
    }

    @Test
    void shouldSlugifyChineseName() {
        String result = SlugValidator.slugify("胡炎淋技能");
        assertEquals("胡炎淋技能", result);
    }

    @Test
    void shouldSlugifyMixedUnicodeName() {
        String result = SlugValidator.slugify("My 技能 Package!");
        assertEquals("my-技能-package", result);
    }
}
