package com.iflytek.skillhub.domain.bundle;

/**
 * Domain-level error for bundle workflows. Message is the i18n key (e.g.
 * {@code error.skillBundle.manifest.missing}).
 */
public class SkillBundleException extends RuntimeException {
    public SkillBundleException(String messageCode) {
        super(messageCode);
    }
}
