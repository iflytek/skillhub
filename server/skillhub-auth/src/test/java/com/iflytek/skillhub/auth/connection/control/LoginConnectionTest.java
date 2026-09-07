package com.iflytek.skillhub.auth.connection.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.auth.connection.core.AdapterKey;
import com.iflytek.skillhub.auth.connection.core.ConnectionUnavailableException;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LoginConnectionTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-08T04:00:00Z");

    @Test
    void draftAndSuccessfullyTestedConnectionCannotStartUntilExplicitActivation() {
        LoginConnection connection = connection();

        assertThat(connection.getStatus()).isEqualTo(LoginConnectionStatus.DRAFT);
        assertThatThrownBy(connection::requireStartableRevisionId)
                .isInstanceOf(ConnectionUnavailableException.class);

        connection.recordSuccessfulTest("revision-1", CREATED_AT.plusSeconds(10));

        assertThat(connection.getStatus()).isEqualTo(LoginConnectionStatus.DRAFT);
        assertThat(connection.getLastTestedRevisionId()).contains("revision-1");
        assertThat(connection.getActiveRevisionId()).isEmpty();
        assertThatThrownBy(connection::requireStartableRevisionId)
                .isInstanceOf(ConnectionUnavailableException.class);

        connection.activate("revision-1", CREATED_AT.plusSeconds(20));

        assertThat(connection.getStatus()).isEqualTo(LoginConnectionStatus.ACTIVE);
        assertThat(connection.requireStartableRevisionId()).isEqualTo("revision-1");
    }

    @Test
    void activationRejectsUntestedOrPreviouslyTestedRevision() {
        LoginConnection connection = connection();

        assertThatThrownBy(() -> connection.activate(
                "revision-1",
                CREATED_AT.plusSeconds(10)
        )).isInstanceOf(DomainBadRequestException.class)
                .hasMessage("error.loginConnection.revision.notTested");

        connection.recordSuccessfulTest("revision-1", CREATED_AT.plusSeconds(20));
        connection.recordSuccessfulTest("revision-2", CREATED_AT.plusSeconds(30));

        assertThatThrownBy(() -> connection.activate(
                "revision-1",
                CREATED_AT.plusSeconds(40)
        )).isInstanceOf(DomainBadRequestException.class)
                .hasMessage("error.loginConnection.revision.notTested");
    }

    @Test
    void testingNewRevisionDoesNotInterruptCurrentActiveSnapshot() {
        LoginConnection connection = connection();
        connection.recordSuccessfulTest("revision-1", CREATED_AT.plusSeconds(10));
        connection.activate("revision-1", CREATED_AT.plusSeconds(20));

        connection.recordSuccessfulTest("revision-2", CREATED_AT.plusSeconds(30));

        assertThat(connection.getStatus()).isEqualTo(LoginConnectionStatus.ACTIVE);
        assertThat(connection.requireStartableRevisionId()).isEqualTo("revision-1");
        assertThat(connection.getLastTestedRevisionId()).contains("revision-2");

        connection.activate("revision-2", CREATED_AT.plusSeconds(40));

        assertThat(connection.requireStartableRevisionId()).isEqualTo("revision-2");
    }

    @Test
    void suspensionBlocksStartsAndRequiresExplicitReactivation() {
        LoginConnection connection = activeConnection();

        connection.suspend(CREATED_AT.plusSeconds(30));
        connection.suspend(CREATED_AT.plusSeconds(40));

        assertThat(connection.getStatus()).isEqualTo(LoginConnectionStatus.SUSPENDED);
        assertThat(connection.getActiveRevisionId()).contains("revision-1");
        assertThatThrownBy(connection::requireStartableRevisionId)
                .isInstanceOf(ConnectionUnavailableException.class);

        connection.activate("revision-1", CREATED_AT.plusSeconds(50));

        assertThat(connection.getStatus()).isEqualTo(LoginConnectionStatus.ACTIVE);
        assertThat(connection.requireStartableRevisionId()).isEqualTo("revision-1");
    }

    @Test
    void disableIsIdempotentTerminalAndDraftCannotBeSuspended() {
        LoginConnection draft = connection();
        assertThatThrownBy(() -> draft.suspend(CREATED_AT.plusSeconds(10)))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessage("error.loginConnection.state.transition.invalid");

        LoginConnection connection = activeConnection();
        connection.disable(CREATED_AT.plusSeconds(30));
        connection.disable(CREATED_AT.plusSeconds(40));

        assertThat(connection.getStatus()).isEqualTo(LoginConnectionStatus.DISABLED);
        assertThatThrownBy(connection::requireStartableRevisionId)
                .isInstanceOf(ConnectionUnavailableException.class);
        assertThatThrownBy(() -> connection.recordSuccessfulTest(
                "revision-2",
                CREATED_AT.plusSeconds(50)
        )).isInstanceOf(DomainBadRequestException.class)
                .hasMessage("error.loginConnection.disabled");
        assertThatThrownBy(() -> connection.activate(
                "revision-1",
                CREATED_AT.plusSeconds(50)
        )).isInstanceOf(DomainBadRequestException.class)
                .hasMessage("error.loginConnection.disabled");
    }

    @Test
    void staleLifecycleCommandFailsClosed() {
        LoginConnection connection = connection();
        connection.recordSuccessfulTest("revision-1", CREATED_AT.plusSeconds(20));

        assertThatThrownBy(() -> connection.activate(
                "revision-1",
                CREATED_AT.plusSeconds(10)
        )).isInstanceOf(DomainBadRequestException.class)
                .hasMessage("error.loginConnection.state.transition.stale");
    }

    private static LoginConnection activeConnection() {
        LoginConnection connection = connection();
        connection.recordSuccessfulTest("revision-1", CREATED_AT.plusSeconds(10));
        connection.activate("revision-1", CREATED_AT.plusSeconds(20));
        return connection;
    }

    private static LoginConnection connection() {
        return LoginConnection.createOrganization(
                "organization-1",
                "Corporate login",
                new AdapterKey("test-redirect"),
                "identity-admin",
                CREATED_AT
        );
    }
}
