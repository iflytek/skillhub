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
}
