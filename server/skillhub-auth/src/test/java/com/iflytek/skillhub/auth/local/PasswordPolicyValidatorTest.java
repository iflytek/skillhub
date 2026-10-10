package com.iflytek.skillhub.auth.local;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyValidatorTest {

    private final PasswordPolicyValidator validator = new PasswordPolicyValidator();

    @Test
    void validPassword_passes() {
        assertThat(validator.validate("Abcdef1!")).isEmpty();
    }

    @Test
    void tooShort_fails() {
        assertThat(validator.validate("Ab1!xyz")).containsExactly("error.auth.local.password.tooShort");
    }

    @Test
    void tooLong_fails() {
        assertThat(validator.validate("A".repeat(129))).containsExactly("error.auth.local.password.tooLong");
    }

    @Test
    void twoCharTypes_fails() {
        assertThat(validator.validate("abcdefgh1")).containsExactly("error.auth.local.password.tooWeak");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Abcdefg1", "Abcdef1!", "abcdef1!", "ABCDEF1!"})
    void threeCharTypes_pass(String password) {
        assertThat(validator.validate(password)).isEmpty();
    }
}
