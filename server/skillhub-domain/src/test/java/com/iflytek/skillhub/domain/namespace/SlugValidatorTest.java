package com.iflytek.skillhub.domain.namespace;

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
        Exception ex = assertThrows(IllegalArgumentException.class, () -> SlugValidator.validate("a"));
        assertTrue(ex.getMessage().contains("length"));
    }
    @Test
    void shouldRejectTooLong() {
        String longSlug = "a".repeat(65);
        Exception ex = assertThrows(IllegalArgumentException.class, () -> SlugValidator.validate(longSlug));
        assertTrue(ex.getMessage().contains("length"));
    }
    @Test
    void shouldRejectUpperCase() {
        Exception ex = assertThrows(IllegalArgumentException.class, () -> SlugValidator.validate("MyNamespace"));
        assertTrue(ex.getMessage().contains("lowercase"));
    }
    @Test
    void shouldRejectStartingWithHyphen() {
        Exception ex = assertThrows(IllegalArgumentException.class, () -> SlugValidator.validate("-namespace"));
        assertTrue(ex.getMessage().contains("alphanumeric"));
    }
    @Test
    void shouldRejectEndingWithHyphen() {
        Exception ex = assertThrows(IllegalArgumentException.class, () -> SlugValidator.validate("namespace-"));
        assertTrue(ex.getMessage().contains("alphanumeric"));
    }
    @Test
    void shouldRejectDoubleHyphen() {
        Exception ex = assertThrows(IllegalArgumentException.class, () -> SlugValidator.validate("my--namespace"));
        assertTrue(ex.getMessage().contains("consecutive"));
    }
    @Test
    void shouldRejectReservedWords() {
        assertThrows(IllegalArgumentException.class, () -> SlugValidator.validate("admin"));
        assertThrows(IllegalArgumentException.class, () -> SlugValidator.validate("api"));
        assertThrows(IllegalArgumentException.class, () -> SlugValidator.validate("global"));
        assertThrows(IllegalArgumentException.class, () -> SlugValidator.validate("system"));
    }
    @Test
    void shouldRejectSpecialCharacters() {
        Exception ex = assertThrows(IllegalArgumentException.class, () -> SlugValidator.validate("my_namespace"));
        assertTrue(ex.getMessage().contains("lowercase") || ex.getMessage().contains("alphanumeric"));
    }
}
