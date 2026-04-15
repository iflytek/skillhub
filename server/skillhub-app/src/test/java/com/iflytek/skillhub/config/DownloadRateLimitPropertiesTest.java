package com.iflytek.skillhub.config;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DownloadRateLimitPropertiesTest {

    @Test
    void validate_shouldFailWhenAnonymousCookieSecretMissing() {
        DownloadRateLimitProperties properties = new DownloadRateLimitProperties();
        properties.setAnonymousCookieSecret(" ");

        assertThrows(IllegalStateException.class, properties::validate);
    }
}
