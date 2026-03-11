package com.iflytek.skillhub.domain.skill.validation;

public class NoOpPrePublishValidator implements PrePublishValidator {

    @Override
    public ValidationResult validate(SkillPackageContext context) {
        return ValidationResult.pass();
    }
}
