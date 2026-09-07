package com.iflytek.skillhub.auth.connection.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reference registry that isolates immutable data-plane snapshots from control-plane revisions.
 * Persistent implementations must preserve the same atomic pointer-switch semantics.
 */
public final class LoginConnectionRuntimeRegistry
        implements EnterpriseConnectionRegistry, LoginConnectionActivationControl {

    private final AdapterDescriptorRegistry installedAdapters;
    private final AtomicReference<Map<ConnectionHandle, LoginConnectionRuntimeSnapshot<?>>> active =
            new AtomicReference<>(Map.of());

    public LoginConnectionRuntimeRegistry(AdapterDescriptorRegistry installedAdapters) {
        this.installedAdapters = Objects.requireNonNull(installedAdapters, "installedAdapters");
    }

    @Override
    public LoginConnectionRuntimeSnapshot<?> requireActive(ConnectionHandle handle) {
        Objects.requireNonNull(handle, "handle");
        LoginConnectionRuntimeSnapshot<?> snapshot = active.get().get(handle);
        if (snapshot == null) {
            throw new ConnectionUnavailableException();
        }
        return snapshot;
    }

    @Override
    public <C extends LoginConnectionRuntimeConfig> LoginConnectionRuntimeSnapshot<C> activate(
            LoginConnectionRevision<C> revision
    ) {
        Objects.requireNonNull(revision, "revision");
        validateInstalledCompatibility(revision.descriptor());
        LoginConnectionRuntimeSnapshot<C> replacement = revision.toRuntimeSnapshot();
        active.updateAndGet(current -> {
            LoginConnectionRuntimeSnapshot<?> existing = current.get(revision.handle());
            if (existing != null) {
                requireSameConnection(existing, revision);
                if (revision.revision() <= existing.revision()) {
                    throw incompatible(
                            ConnectionRevisionIncompatibility.NON_MONOTONIC_ACTIVATION,
                            "activation requires a newer connection revision"
                    );
                }
            }
            return withReplacement(current, revision.handle(), replacement);
        });
        return replacement;
    }

    @Override
    public <C extends LoginConnectionRuntimeConfig> LoginConnectionRuntimeSnapshot<C> rollback(
            LoginConnectionRevision<C> revision
    ) {
        Objects.requireNonNull(revision, "revision");
        validateInstalledCompatibility(revision.descriptor());
        LoginConnectionRuntimeSnapshot<C> replacement = revision.toRuntimeSnapshot();
        active.updateAndGet(current -> {
            LoginConnectionRuntimeSnapshot<?> existing = current.get(revision.handle());
            if (existing == null) {
                throw new ConnectionUnavailableException();
            }
            requireSameConnection(existing, revision);
            if (revision.revision() >= existing.revision()) {
                throw incompatible(
                        ConnectionRevisionIncompatibility.INVALID_ROLLBACK_TARGET,
                        "rollback requires an older connection revision"
                );
            }
            return withReplacement(current, revision.handle(), replacement);
        });
        return replacement;
    }

    private void validateInstalledCompatibility(AdapterDescriptor revisionDescriptor) {
        AdapterDescriptorRegistry.validateDescriptor(revisionDescriptor);
        AdapterDescriptor installed = installedAdapters.require(
                revisionDescriptor.adapterKey(),
                revisionDescriptor.contractVersion().major()
        );
        if (installed.connectionKind() != ConnectionKind.LOGIN
                || !installed.interactionModel().equals(revisionDescriptor.interactionModel())) {
            throw incompatible(
                    ConnectionRevisionIncompatibility.DESCRIPTOR_SHAPE_UNSUPPORTED,
                    "installed adapter interaction does not match the revision"
            );
        }
        if (installed.contractVersion().minor() < revisionDescriptor.contractVersion().minor()) {
            throw incompatible(
                    ConnectionRevisionIncompatibility.CONTRACT_MINOR_UNSUPPORTED,
                    "installed adapter contract minor is older than the revision"
            );
        }
        if (installed.configSchemaVersion() != revisionDescriptor.configSchemaVersion()) {
            throw incompatible(
                    ConnectionRevisionIncompatibility.CONFIG_SCHEMA_UNSUPPORTED,
                    "installed adapter cannot materialize the revision config schema"
            );
        }
        if (!installed.capabilities().containsAll(revisionDescriptor.capabilities())) {
            throw incompatible(
                    ConnectionRevisionIncompatibility.CAPABILITY_UNSUPPORTED,
                    "installed adapter does not provide every revision capability"
            );
        }
    }

    private static void requireSameConnection(
            LoginConnectionRuntimeSnapshot<?> existing,
            LoginConnectionRevision<?> revision
    ) {
        if (!existing.connectionId().equals(revision.connectionId())
                || !existing.organizationId().equals(revision.organizationId())) {
            throw incompatible(
                    ConnectionRevisionIncompatibility.CONNECTION_IDENTITY_MISMATCH,
                    "active pointer and revision identify different connections"
            );
        }
    }

    private static Map<ConnectionHandle, LoginConnectionRuntimeSnapshot<?>> withReplacement(
            Map<ConnectionHandle, LoginConnectionRuntimeSnapshot<?>> current,
            ConnectionHandle handle,
            LoginConnectionRuntimeSnapshot<?> replacement
    ) {
        Map<ConnectionHandle, LoginConnectionRuntimeSnapshot<?>> updated = new HashMap<>(current);
        updated.put(handle, replacement);
        return Map.copyOf(updated);
    }

    private static ConnectionRevisionIncompatibleException incompatible(
            ConnectionRevisionIncompatibility reason,
            String message
    ) {
        return new ConnectionRevisionIncompatibleException(reason, message);
    }
}
