package com.iflytek.skillhub.auth.local;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Validates local-account passwords against the platform's length and character diversity rules.
 */
@Component
public class PasswordPolicyValidator {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 128;
    private static final int MIN_CHAR_TYPES = 3;

    public List<String> validate(String password) {
        List<String> errors = new ArrayList<>();
        if (password == null || password.length() < MIN_LENGTH) {
            errors.add("error.auth.local.password.tooShort");
            return errors;
        }
        if (password.length() > MAX_LENGTH) {
            errors.add("error.auth.local.password.tooLong");
            return errors;
        }

        int typeCount = 0;
        if (password.chars().anyMatch(Character::isLowerCase)) {
            typeCount++;
        }
        if (password.chars().anyMatch(Character::isUpperCase)) {
            typeCount++;
        }
        if (password.chars().anyMatch(Character::isDigit)) {
            typeCount++;
        }
        if (password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch))) {
            typeCount++;
        }
        if (typeCount < MIN_CHAR_TYPES) {
            errors.add("error.auth.local.password.tooWeak");
        }
        return errors;
    }
}
