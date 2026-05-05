package com.iflytek.skillhub.auth.oauth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthLoginRedirectSupportTest {

    @Test
    void privateConstructor_canBeInvokedViaReflection() throws Exception {
        java.lang.reflect.Constructor<OAuthLoginRedirectSupport> constructor =
                OAuthLoginRedirectSupport.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        OAuthLoginRedirectSupport instance = constructor.newInstance();
        assertThat(instance).isNotNull();
    }

    @Test
    void sanitizeReturnTo_acceptsValidRelativePath() {
        assertThat(OAuthLoginRedirectSupport.sanitizeReturnTo("/dashboard")).isEqualTo("/dashboard");
        assertThat(OAuthLoginRedirectSupport.sanitizeReturnTo("/dashboard/publish?draft=1"))
                .isEqualTo("/dashboard/publish?draft=1");
    }

    @Test
    void sanitizeReturnTo_rejectsNullAndBlank() {
        assertThat(OAuthLoginRedirectSupport.sanitizeReturnTo(null)).isNull();
        assertThat(OAuthLoginRedirectSupport.sanitizeReturnTo("")).isNull();
        assertThat(OAuthLoginRedirectSupport.sanitizeReturnTo("   ")).isNull();
    }

    @Test
    void sanitizeReturnTo_rejectsAbsoluteUrlsAndProtocolRelative() {
        assertThat(OAuthLoginRedirectSupport.sanitizeReturnTo("https://evil.example")).isNull();
        assertThat(OAuthLoginRedirectSupport.sanitizeReturnTo("//evil.example")).isNull();
    }

    @Test
    void sanitizeReturnTo_rejectsLineBreaks() {
        assertThat(OAuthLoginRedirectSupport.sanitizeReturnTo("/dashboard\n/evil")).isNull();
        assertThat(OAuthLoginRedirectSupport.sanitizeReturnTo("/dashboard\r/evil")).isNull();
    }
}
