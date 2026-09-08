package com.iflytek.skillhub.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.connection.control.LoginConnection;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionControlAdapterRegistry;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionRepository;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionRevisionRepository;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionStatus;
import com.iflytek.skillhub.auth.connection.control.StoredLoginConnectionRevision;
import com.iflytek.skillhub.auth.connection.secret.LoginConnectionSecretService;
import com.iflytek.skillhub.auth.connection.secret.SecretConfigurationSummary;
import com.iflytek.skillhub.auth.operation.IdentityOperation;
import com.iflytek.skillhub.auth.operation.IdentityOperationRepository;
import com.iflytek.skillhub.auth.operation.IdentityOperationStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.LoginConnectionHealthResponse;
import com.iflytek.skillhub.dto.LoginConnectionResponse;
import com.iflytek.skillhub.dto.LoginConnectionRevisionResponse;
import com.iflytek.skillhub.dto.LoginConnectionSecretSummaryResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Joins connection state, immutable revision, safe secret summary and latest test health. */
@Repository
public class EnterpriseConnectionQueryRepository {

    private static final TypeReference<Map<String, Object>> CONFIG_TYPE = new TypeReference<>() { };

    private final LoginConnectionRepository connections;
    private final LoginConnectionRevisionRepository revisions;
    private final IdentityOperationRepository operations;
    private final ObjectProvider<LoginConnectionControlAdapterRegistry> adapterRegistry;
    private final ObjectProvider<LoginConnectionSecretService> secretService;
    private final ObjectMapper objectMapper;

    public EnterpriseConnectionQueryRepository(
            LoginConnectionRepository connections,
            LoginConnectionRevisionRepository revisions,
            IdentityOperationRepository operations,
            ObjectProvider<LoginConnectionControlAdapterRegistry> adapterRegistry,
            ObjectProvider<LoginConnectionSecretService> secretService,
            ObjectMapper objectMapper
    ) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.revisions = Objects.requireNonNull(revisions, "revisions");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.adapterRegistry = Objects.requireNonNull(adapterRegistry, "adapterRegistry");
        this.secretService = Objects.requireNonNull(secretService, "secretService");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Transactional(readOnly = true)
    public List<LoginConnectionResponse> findAll(String organizationId) {
        requireControlPlane();
        return connections.findAllByOrganizationId(organizationId).stream()
                .map(connection -> response(organizationId, connection))
                .toList();
    }

    @Transactional(readOnly = true)
    public LoginConnectionResponse find(String organizationId, String connectionId) {
        requireControlPlane();
        LoginConnection connection = connections.findByOrganizationIdAndId(
                organizationId,
                connectionId
        ).orElseThrow(() -> new DomainNotFoundException("error.loginConnection.notFound"));
        return response(organizationId, connection);
    }

    private LoginConnectionResponse response(String organizationId, LoginConnection connection) {
        StoredLoginConnectionRevision latest = revisions.findLatestByConnectionId(connection.getId())
                .orElse(null);
        IdentityOperation latestTest = operations.findLatestLoginConnectionTest(
                organizationId,
                connection.getId()
        ).orElse(null);
        SecretConfigurationSummary secret = requireRegistry().require(connection.getAdapterKey())
                .secretPurpose()
                .map(purpose -> requireSecrets().describe(
                        organizationId,
                        connection.getId(),
                        purpose
                ))
                .orElseGet(SecretConfigurationSummary::notConfigured);
        return new LoginConnectionResponse(
                connection.getId(),
                connection.getPublicHandle().value(),
                connection.getDisplayName(),
                connection.getAdapterKey().value(),
                connection.getStatus().name(),
                connection.getActiveRevisionId().orElse(null),
                connection.getLastTestedRevisionId().orElse(null),
                latest == null ? null : revisionResponse(latest),
                new LoginConnectionSecretSummaryResponse(
                        secret.configured(),
                        secret.updatedAt(),
                        secret.previousValidUntil()
                ),
                health(connection, latest, latestTest),
                connection.getCreatedAt(),
                connection.getUpdatedAt()
        );
    }

    private LoginConnectionHealthResponse health(
            LoginConnection connection,
            StoredLoginConnectionRevision latest,
            IdentityOperation latestTest
    ) {
        if (connection.getStatus() == LoginConnectionStatus.DISABLED) {
            return new LoginConnectionHealthResponse("DISABLED", null, null);
        }
        if (connection.getStatus() == LoginConnectionStatus.SUSPENDED) {
            return new LoginConnectionHealthResponse("SUSPENDED", null, null);
        }
        if (latest == null || latestTest == null
                || !latest.getId().equals(latestTest.getRevisionId())) {
            return new LoginConnectionHealthResponse("UNTESTED", null, null);
        }
        if (latestTest.getStatus() == IdentityOperationStatus.FAILED) {
            return new LoginConnectionHealthResponse(
                    "ERROR",
                    latestTest.getErrorCode().orElse("UNEXPECTED"),
                    latestTest.getCompletedAt()
            );
        }
        String health = connection.getActiveRevisionId()
                .filter(latest.getId()::equals)
                .map(ignored -> "HEALTHY")
                .orElse("READY_TO_ACTIVATE");
        return new LoginConnectionHealthResponse(health, null, latestTest.getCompletedAt());
    }

    private LoginConnectionRevisionResponse revisionResponse(
            StoredLoginConnectionRevision revision
    ) {
        try {
            return new LoginConnectionRevisionResponse(
                    revision.getId(),
                    revision.getRevision(),
                    revision.getAdapterContractVersion(),
                    revision.getConfigSchemaVersion(),
                    Map.copyOf(objectMapper.readValue(revision.getTypedConfig(), CONFIG_TYPE)),
                    revision.correlationPolicy().verifiedEmailCorrelationEnabled(),
                    revision.correlationPolicy().jitProvisioningEnabled(),
                    revision.getCreatedAt()
            );
        } catch (Exception invalidStoredConfiguration) {
            throw new DomainBadRequestException("error.loginConnection.configuration.unreadable");
        }
    }

    private LoginConnectionControlAdapterRegistry requireRegistry() {
        return adapterRegistry.getIfAvailable(() -> {
            throw unavailable();
        });
    }

    private LoginConnectionSecretService requireSecrets() {
        return secretService.getIfAvailable(() -> {
            throw unavailable();
        });
    }

    private void requireControlPlane() {
        requireRegistry();
        requireSecrets();
    }

    private static DomainBadRequestException unavailable() {
        return new DomainBadRequestException("error.loginConnection.controlPlane.unavailable");
    }
}
