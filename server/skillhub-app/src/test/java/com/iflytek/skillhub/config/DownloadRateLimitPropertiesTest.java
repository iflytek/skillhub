package com.iflytek.skillhub.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DownloadRateLimitPropertiesTest {

    @Test
    void anonymousCookieSecretDoesNotDefaultToProductionPlaceholder() {
        DownloadRateLimitProperties properties = new DownloadRateLimitProperties();

        assertThat(properties.getAnonymousCookieSecret())
                .isNull();
    }
}
