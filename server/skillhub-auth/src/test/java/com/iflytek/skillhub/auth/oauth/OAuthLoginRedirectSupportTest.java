package com.iflytek.skillhub.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OAuthLoginRedirectSupportTest {
    @Test
    void defaultTargetIsHomeAndValidSourceIsPreserved() {
        assertThat(OAuthLoginRedirectSupport.DEFAULT_TARGET_URL).isEqualTo("/");
        assertThat(OAuthLoginRedirectSupport.sanitizeReturnTo(" /search?q=player#results "))
                .isEqualTo("/search?q=player#results");
        assertThat(OAuthLoginRedirectSupport.sanitizeReturnTo("/cli/auth")).isEqualTo("/cli/auth");
    }

    @Test
    void unsafeTargetsAreRejected() {
        assertThat(OAuthLoginRedirectSupport.sanitizeReturnTo(null)).isNull();
        for (String target : List.of("", "https://outside.test", "//outside.test", "/\\outside.test", "/sea\nrch", "/search\u007f")) {
            assertThat(OAuthLoginRedirectSupport.sanitizeReturnTo(target)).isNull();
        }
    }
}
