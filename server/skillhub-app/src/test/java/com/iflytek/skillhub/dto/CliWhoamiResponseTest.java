package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CliWhoamiResponseTest {

    @Test
    void from_mapsAllFields_whenEmailAndAvatarPresent() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "u-1", "Alice", "alice@example.com", "https://avatar.png", "github", Set.of("ADMIN")
        );

        CliWhoamiResponse response = CliWhoamiResponse.from(principal);

        assertThat(response.userId()).isEqualTo("u-1");
        assertThat(response.displayName()).isEqualTo("Alice");
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.avatarUrl()).isEqualTo("https://avatar.png");
        assertThat(response.authType()).isEqualTo("github");
        assertThat(response.platformRoles()).containsExactly("ADMIN");
    }

    @Test
    void from_usesEmptyString_whenEmailAndAvatarAreNull() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "u-2", "Bob", null, null, "local", Set.of()
        );

        CliWhoamiResponse response = CliWhoamiResponse.from(principal);

        assertThat(response.email()).isEqualTo("");
        assertThat(response.avatarUrl()).isEqualTo("");
    }
}
