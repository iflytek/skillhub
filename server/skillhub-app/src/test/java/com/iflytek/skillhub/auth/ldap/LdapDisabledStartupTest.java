package com.iflytek.skillhub.auth.ldap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.auth.config.LdapProperties;
import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.local.LocalAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration coverage for "startup with LDAP disabled" required by the PR #437 review: the full
 * Spring context must boot without a directory configured, the conditional {@link LdapAuthService}
 * bean must be absent, and the local login fallback must degrade to invalid credentials instead of
 * failing on a missing LDAP bean.
 */
@SpringBootTest(properties = "skillhub.ldap.enabled=false")
@ActiveProfiles("test")
class LdapDisabledStartupTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private LdapProperties ldapProperties;

    @Autowired
    private LocalAuthService localAuthService;

    @Test
    void contextStartsWithoutLdapAuthServiceBean() {
        assertThat(ldapProperties.isEnabled()).isFalse();
        // The bean is created only when skillhub.ldap.enabled=true; with LDAP disabled the
        // context must start without it (LocalAuthService consumes it via ObjectProvider).
        assertThat(context.getBeansOfType(LdapAuthService.class)).isEmpty();
        assertThat(localAuthService).isNotNull();
    }

    @Test
    void localLogin_withoutLdapBean_fallsBackToInvalidCredentials() {
        assertThatThrownBy(() -> localAuthService.login("no-such-user", "wrong-password"))
            .isInstanceOf(AuthFlowException.class)
            .satisfies(e -> assertThat(((AuthFlowException) e).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
