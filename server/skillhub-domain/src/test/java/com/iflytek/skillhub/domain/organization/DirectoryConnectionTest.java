package com.iflytek.skillhub.domain.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class DirectoryConnectionTest {

    private static final Instant NOW = Instant.parse("2026-11-26T00:00:00Z");

    @Test
    void newConnectionStartsDraftAndAcceptsOnlyActiveState() {
        DirectoryConnection connection = DirectoryConnection.create(
                "org-1", "admin", "a".repeat(64), "token1234", NOW);

        assertThat(connection.getStatus()).isEqualTo(DirectoryConnectionStatus.DRAFT);
        assertThat(connection.acceptsSecret("a".repeat(64))).isFalse();

        connection.activate(NOW.plusSeconds(1));

        assertThat(connection.getStatus()).isEqualTo(DirectoryConnectionStatus.ACTIVE);
        assertThat(connection.acceptsSecret("a".repeat(64))).isTrue();
    }

    @Test
    void suspendAndDisableRejectFurtherRequests() {
        DirectoryConnection connection = DirectoryConnection.create(
                "org-1", "admin", "a".repeat(64), "token1234", NOW);
        connection.activate(NOW.plusSeconds(1));
        connection.suspend(NOW.plusSeconds(2));

        assertThat(connection.acceptsSecret("a".repeat(64))).isFalse();

        connection.disable(NOW.plusSeconds(3));
        assertThat(connection.getStatus()).isEqualTo(DirectoryConnectionStatus.DISABLED);
    }

    @Test
    void staleTransitionIsRejected() {
        DirectoryConnection connection = DirectoryConnection.create(
                "org-1", "admin", "a".repeat(64), "token1234", NOW);

        assertThatThrownBy(() -> connection.activate(NOW.minusSeconds(1)))
                .isInstanceOf(RuntimeException.class);
    }
}
