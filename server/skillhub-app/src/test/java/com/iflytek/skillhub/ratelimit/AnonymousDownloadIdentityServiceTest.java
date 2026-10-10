package com.iflytek.skillhub.ratelimit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.config.DownloadRateLimitProperties;
import org.junit.jupiter.api.Test;

class AnonymousDownloadIdentityServiceTest {

    @Test
    void validateAnonymousCookieSecretRejectsReleaseExamplePlaceholder() {
        DownloadRateLimitProperties properties = new DownloadRateLimitProperties();
        properties.setAnonymousCookieSecret("replace-with-random-download-secret-32-bytes");
        AnonymousDownloadIdentityService service = new AnonymousDownloadIdentityService(properties, new ClientIpResolver());

        assertThatThrownBy(service::validateAnonymousCookieSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not use the default placeholder");
    }
}
