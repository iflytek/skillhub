package com.iflytek.skillhub.auth.ldap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.iflytek.skillhub.auth.config.LdapProperties;
import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.namespace.GlobalNamespaceMembershipService;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Integration coverage for the "directory unavailable" error classification required by the
 * PR #437 review. A real JNDI connection is attempted against a guaranteed-closed localhost
 * port, so the CommunicationException path (not a mocked exception) is exercised end to end.
 *
 * <p>This test intentionally does not use Testcontainers: it needs no directory, and keeping it
 * standalone lets it run in any environment, including ones without a Docker daemon.
 */
class LdapDirectoryUnavailableTest {

    @Test
    void login_whenDirectoryUnreachable_returnsServiceUnavailable() {
        LdapProperties props = new LdapProperties();
        props.setEnabled(true);
        props.setUrl("ldap://127.0.0.1:" + freePort());
        props.setBase("dc=example,dc=org");

        LdapAuthService svc = new LdapAuthService(
            props,
            mock(UserAccountRepository.class),
            mock(UserRoleBindingRepository.class),
            mock(GlobalNamespaceMembershipService.class),
            mock(IdentityBindingRepository.class));

        assertThatThrownBy(() -> svc.login("alice", "secret"))
            .isInstanceOf(AuthFlowException.class)
            .satisfies(e -> {
                AuthFlowException ex = (AuthFlowException) e;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(ex.getMessageCode()).isEqualTo("error.auth.ldap.directoryUnavailable");
            });
    }

    /**
     * Reserves an ephemeral port and releases it, leaving a port that is (almost certainly)
     * closed for the subsequent connection attempt.
     */
    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to allocate an ephemeral port", e);
        }
    }
}
