package com.iflytek.skillhub.auth.connection.control;

import com.iflytek.skillhub.auth.connection.core.ConnectionHandle;
import com.iflytek.skillhub.auth.connection.core.ConnectionUnavailableException;
import com.iflytek.skillhub.auth.connection.core.EnterpriseConnectionRegistry;
import com.iflytek.skillhub.auth.connection.core.LoginConnectionRuntimeSnapshot;
import java.util.Objects;

/** Database-backed data-plane registry for newly started authentication requests. */
public final class PersistentEnterpriseConnectionRegistry implements EnterpriseConnectionRegistry {

    private final LoginConnectionRepository connections;
    private final LoginConnectionRevisionRepository revisions;
    private final LoginConnectionRuntimeSnapshotMaterializer materializer;

    public PersistentEnterpriseConnectionRegistry(
            LoginConnectionRepository connections,
            LoginConnectionRevisionRepository revisions,
            LoginConnectionRuntimeSnapshotMaterializer materializer
    ) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.revisions = Objects.requireNonNull(revisions, "revisions");
        this.materializer = Objects.requireNonNull(materializer, "materializer");
    }

    @Override
    public LoginConnectionRuntimeSnapshot<?> requireActive(ConnectionHandle handle) {
        Objects.requireNonNull(handle, "handle");
        LoginConnection connection = connections.findByPublicHandle(handle)
                .orElseThrow(ConnectionUnavailableException::new);
        String activeRevisionId = connection.requireStartableRevisionId();
        LoginConnectionRevisionSnapshot stored = revisions.findByConnectionIdAndId(
                connection.getId(),
                activeRevisionId
        ).map(revision -> revision.snapshot(connection))
                .orElseThrow(ConnectionUnavailableException::new);
        LoginConnectionRuntimeSnapshot<?> runtime;
        try {
            runtime = materializer.materialize(stored);
        } catch (ConnectionUnavailableException unavailable) {
            throw unavailable;
        } catch (RuntimeException materializationFailure) {
            throw new ConnectionUnavailableException();
        }
        if (runtime == null) {
            throw new ConnectionUnavailableException();
        }
        requireSameRevision(handle, stored, runtime);
        return runtime;
    }

    private static void requireSameRevision(
            ConnectionHandle requestedHandle,
            LoginConnectionRevisionSnapshot stored,
            LoginConnectionRuntimeSnapshot<?> runtime
    ) {
        String runtimeContractVersion = runtime.adapterContractVersion().major()
                + "." + runtime.adapterContractVersion().minor();
        boolean same = runtime.handle().equals(requestedHandle)
                && runtime.handle().equals(stored.publicHandle())
                && runtime.organizationId().equals(stored.organizationId())
                && runtime.connectionId().equals(stored.connectionId())
                && runtime.revision() == stored.revision()
                && runtime.adapterKey().equals(stored.adapterKey())
                && runtimeContractVersion.equals(stored.adapterContractVersion())
                && runtime.configSchemaVersion() == stored.configSchemaVersion();
        if (!same) {
            throw new ConnectionUnavailableException();
        }
    }
}
