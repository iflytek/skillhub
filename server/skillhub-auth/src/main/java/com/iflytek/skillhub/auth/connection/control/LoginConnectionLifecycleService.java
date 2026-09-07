package com.iflytek.skillhub.auth.connection.control;

import com.iflytek.skillhub.auth.connection.core.ConnectionUnavailableException;
import com.iflytek.skillhub.auth.federation.adapter.RemoteIdentityIoExecutor;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Protocol-neutral control plane for testing, activating and stopping login connections.
 * Remote probes are suspended from ambient database transactions before tested state is written.
 */
@Service
public class LoginConnectionLifecycleService {

    private final LoginConnectionRepository connections;
    private final LoginConnectionRevisionRepository revisions;
    private final RemoteIdentityIoExecutor remoteIdentityIo;

    public LoginConnectionLifecycleService(
            LoginConnectionRepository connections,
            LoginConnectionRevisionRepository revisions,
            RemoteIdentityIoExecutor remoteIdentityIo
    ) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.revisions = Objects.requireNonNull(revisions, "revisions");
        this.remoteIdentityIo = Objects.requireNonNull(remoteIdentityIo, "remoteIdentityIo");
    }

    public LoginConnectionRevisionSnapshot testRevision(
            String organizationId,
            String connectionId,
            String revisionId,
            LoginConnectionTestProbe probe,
            Instant occurredAt
    ) {
        Objects.requireNonNull(probe, "probe");
        LoginConnectionRevisionSnapshot candidate = loadRevisionSnapshot(
                organizationId,
                connectionId,
                revisionId
        );
        remoteIdentityIo.execute(() -> {
            probe.verify(candidate);
            return null;
        });

        // Reload after remote I/O: a concurrent disable must win over a stale successful probe.
        LoginConnection connection = requireConnection(organizationId, connectionId);
        requireRevision(connection, revisionId);
        connection.recordSuccessfulTest(revisionId, occurredAt);
        connections.save(connection);
        return candidate;
    }

    public LoginConnectionRevisionSnapshot activate(
            String organizationId,
            String connectionId,
            String revisionId,
            Instant occurredAt
    ) {
        LoginConnection connection = requireConnection(organizationId, connectionId);
        StoredLoginConnectionRevision revision = requireRevision(connection, revisionId);
        connection.activate(revisionId, occurredAt);
        LoginConnection saved = connections.save(connection);
        return revision.snapshot(saved);
    }

    public LoginConnection suspend(
            String organizationId,
            String connectionId,
            Instant occurredAt
    ) {
        LoginConnection connection = requireConnection(organizationId, connectionId);
        connection.suspend(occurredAt);
        return connections.save(connection);
    }

    public LoginConnection disable(
            String organizationId,
            String connectionId,
            Instant occurredAt
    ) {
        LoginConnection connection = requireConnection(organizationId, connectionId);
        connection.disable(occurredAt);
        return connections.save(connection);
    }

    /** Loads one immutable revision for a new authentication request. */
    public LoginConnectionRevisionSnapshot requireStartable(
            String organizationId,
            String connectionId
    ) {
        LoginConnection connection = requireConnection(organizationId, connectionId);
        String activeRevisionId = connection.requireStartableRevisionId();
        return revisions.findByConnectionIdAndId(connection.getId(), activeRevisionId)
                .map(revision -> revision.snapshot(connection))
                .orElseThrow(ConnectionUnavailableException::new);
    }

    private LoginConnectionRevisionSnapshot loadRevisionSnapshot(
            String organizationId,
            String connectionId,
            String revisionId
    ) {
        LoginConnection connection = requireConnection(organizationId, connectionId);
        return requireRevision(connection, revisionId).snapshot(connection);
    }

    private LoginConnection requireConnection(String organizationId, String connectionId) {
        return connections.findByOrganizationIdAndId(
                requireText(organizationId, "organizationId"),
                requireText(connectionId, "connectionId")
        ).orElseThrow(() -> new DomainNotFoundException("error.loginConnection.notFound"));
    }

    private StoredLoginConnectionRevision requireRevision(
            LoginConnection connection,
            String revisionId
    ) {
        return revisions.findByConnectionIdAndId(
                connection.getId(),
                requireText(revisionId, "revisionId")
        ).orElseThrow(() -> new DomainNotFoundException("error.loginConnection.revision.notFound"));
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
