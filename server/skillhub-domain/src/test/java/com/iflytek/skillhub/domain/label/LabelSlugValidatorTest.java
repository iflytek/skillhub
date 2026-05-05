package com.iflytek.skillhub.domain.label;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LabelSlugValidatorTest {

    @Test
    void normalizeShouldLowercaseValidSlug() {
        assertEquals("code-generation", LabelSlugValidator.normalize(" Code-Generation "));
    }

    @Test
    void normalizeShouldRejectSpecialCharacters() {
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> LabelSlugValidator.normalize("code_generation"));
        assertEquals("error.slug.pattern", ex.messageCode());
    }

    @Test
    void normalizeShouldRejectDoubleHyphen() {
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> LabelSlugValidator.normalize("code--generation"));
        assertEquals("error.slug.doubleHyphen", ex.messageCode());
    }

    @Test
    void normalizeShouldRejectNull() {
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> LabelSlugValidator.normalize(null));
        assertEquals("error.slug.blank", ex.messageCode());
    }

    @Test
    void normalizeShouldRejectBlank() {
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> LabelSlugValidator.normalize("   "));
        assertEquals("error.slug.blank", ex.messageCode());
    }

    @Test
    void validateShouldRejectNull() {
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> LabelSlugValidator.validate(null));
        assertEquals("error.slug.blank", ex.messageCode());
    }

    @Test
    void validateShouldRejectBlank() {
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> LabelSlugValidator.validate(""));
        assertEquals("error.slug.blank", ex.messageCode());
    }

    @Test
    void validateShouldRejectTooShort() {
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> LabelSlugValidator.validate(""));
        assertEquals("error.slug.blank", ex.messageCode());
    }

    @Test
    void validateShouldRejectTooLong() {
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> LabelSlugValidator.validate("a".repeat(65)));
        assertEquals("error.slug.length", ex.messageCode());
    }

    @Test
    void validateShouldRejectInvalidPattern() {
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> LabelSlugValidator.validate("_invalid_"));
        assertEquals("error.slug.pattern", ex.messageCode());
    }

    @Test
    void validateShouldRejectDoubleHyphen() {
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> LabelSlugValidator.validate("code--generation"));
        assertEquals("error.slug.doubleHyphen", ex.messageCode());
    }
}
