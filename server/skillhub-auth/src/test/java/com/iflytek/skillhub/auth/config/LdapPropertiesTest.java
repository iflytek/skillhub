package com.iflytek.skillhub.auth.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Startup configuration validation for the LDAP properties block.
 */
class LdapPropertiesTest {

    @Test
    void validate_requiresUrlAndBase_whenEnabled() {
        LdapProperties props = new LdapProperties();
        props.setEnabled(true);

        assertThatThrownBy(props::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("skillhub.ldap.url");
    }

    @Test
    void validate_rejectsNonPositiveTimeouts_whenEnabled() {
        LdapProperties props = new LdapProperties();
        props.setEnabled(true);
        props.setUrl("ldap://localhost:389");
        props.setBase("dc=example,dc=org");
        props.setConnectTimeoutMillis(0);

        assertThatThrownBy(props::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("connect-timeout-millis");
    }

    @Test
    void validate_skipsChecks_whenDisabled() {
        LdapProperties props = new LdapProperties();
        assertThatCode(props::validate).doesNotThrowAnyException();
    }
}
