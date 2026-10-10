package com.iflytek.skillhub.domain.skill.validation;

/**
 * Trivial validator used when no extra pre-publish checks are desired.
 */
public class NoOpPrePublishValidator implements PrePublishValidator {

    @Override
    public ValidationResult validate(SkillPackageContext context) {
        return ValidationResult.pass();
    }
}
