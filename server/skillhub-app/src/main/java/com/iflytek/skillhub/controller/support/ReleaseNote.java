package com.iflytek.skillhub.controller.support;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;

/**
 * Normalizes the optional release note both publish surfaces accept.
 *
 * <p>Keeping the trim and the cap in one place means the web and CLI endpoints cannot drift, and the
 * rule can be asserted without standing up either controller.
 */
public final class ReleaseNote {

    public static final int MAX_LENGTH = 4000;

    private ReleaseNote() {
    }

    public static String normalize(String changelog) {
        if (changelog == null || changelog.isBlank()) {
            return null;
        }
        String trimmed = changelog.strip();
        if (trimmed.length() > MAX_LENGTH) {
            throw new DomainBadRequestException("error.skill.publish.changelog.tooLong");
        }
        return trimmed;
    }
}
