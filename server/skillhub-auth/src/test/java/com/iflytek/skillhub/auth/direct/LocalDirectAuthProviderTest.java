package com.iflytek.skillhub.auth.direct;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.local.LocalAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LocalDirectAuthProviderTest {

    @Test
    void providerCode_returnsLocal() {
        LocalAuthService localAuthService = mock(LocalAuthService.class);
        LocalDirectAuthProvider provider = new LocalDirectAuthProvider(localAuthService);

        assertThat(provider.providerCode()).isEqualTo("local");
    }

    @Test
    void displayName_returnsLocalAccount() {
        LocalAuthService localAuthService = mock(LocalAuthService.class);
        LocalDirectAuthProvider provider = new LocalDirectAuthProvider(localAuthService);

        assertThat(provider.displayName()).isEqualTo("Local Account");
    }

    @Test
    void authenticate_delegatesToLocalAuthService() {
        LocalAuthService localAuthService = mock(LocalAuthService.class);
        LocalDirectAuthProvider provider = new LocalDirectAuthProvider(localAuthService);

        PlatformPrincipal principal = new PlatformPrincipal(
                "usr_1", "Alice", "alice@example.com", null,
                "local", Set.of("USER")
        );
        when(localAuthService.login("alice", "secret")).thenReturn(principal);

        DirectAuthRequest request = new DirectAuthRequest("alice", "secret");
        PlatformPrincipal result = provider.authenticate(request);

        assertThat(result).isEqualTo(principal);
    }
}
