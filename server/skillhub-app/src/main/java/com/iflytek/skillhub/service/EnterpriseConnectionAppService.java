package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.connection.control.LoginConnection;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionConfigurationInput;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionControlAdapter;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionControlAdapterRegistry;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionLifecycleService;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionRepository;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionRevisionRepository;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionStatus;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionTestException;
import com.iflytek.skillhub.auth.connection.control.PreparedLoginConnectionRevision;
import com.iflytek.skillhub.auth.connection.control.StoredLoginConnectionRevision;
import com.iflytek.skillhub.auth.connection.core.AdapterKey;
import com.iflytek.skillhub.auth.connection.secret.LoginConnectionSecretService;
import com.iflytek.skillhub.auth.connection.secret.SecretMaterial;
import com.iflytek.skillhub.auth.connection.secret.SecretPurpose;
import com.iflytek.skillhub.auth.operation.IdentityOperation;
import com.iflytek.skillhub.auth.operation.IdentityOperationRepository;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.audit.OrganizationAuditAction;
import com.iflytek.skillhub.domain.audit.OrganizationAuditDetail;
import com.iflytek.skillhub.domain.audit.OrganizationAuditEvent;
import com.iflytek.skillhub.domain.audit.OrganizationAuditTargetType;
import com.iflytek.skillhub.domain.organization.OrganizationAdministrativeAction;
import com.iflytek.skillhub.domain.organization.OrganizationAuthorizationService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.LoginConnectionCreateRequest;
import com.iflytek.skillhub.dto.LoginConnectionResponse;
import com.iflytek.skillhub.dto.LoginConnectionRevisionCreateRequest;
import com.iflytek.skillhub.dto.LoginConnectionTestResponse;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.EnterpriseConnectionQueryRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Protocol-neutral Organization control plane for versioned enterprise login connections. */
@Service
public class EnterpriseConnectionAppService {

    private final OrganizationAuthorizationService authorization;
    private final LoginConnectionRepository connections;
    private final LoginConnectionRevisionRepository revisions;
    private final LoginConnectionLifecycleService lifecycle;
    private final IdentityOperationRepository operations;
    private final AuditLogService auditLogService;
    private final EnterpriseConnectionQueryRepository queryRepository;
    private final ObjectProvider<LoginConnectionControlAdapterRegistry> adapterRegistry;
    private final ObjectProvider<LoginConnectionSecretService> secretService;
    private final RequestIdAccessor requestIds;
    private final Clock clock;

    public EnterpriseConnectionAppService(
            OrganizationAuthorizationService authorization,
            LoginConnectionRepository connections,
            LoginConnectionRevisionRepository revisions,
            LoginConnectionLifecycleService lifecycle,
            IdentityOperationRepository operations,
            AuditLogService auditLogService,
            EnterpriseConnectionQueryRepository queryRepository,
            ObjectProvider<LoginConnectionControlAdapterRegistry> adapterRegistry,
            ObjectProvider<LoginConnectionSecretService> secretService,
            RequestIdAccessor requestIds,
            Clock clock
    ) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.connections = Objects.requireNonNull(connections, "connections");
        this.revisions = Objects.requireNonNull(revisions, "revisions");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.auditLogService = Objects.requireNonNull(auditLogService, "auditLogService");
        this.queryRepository = Objects.requireNonNull(queryRepository, "queryRepository");
        this.adapterRegistry = Objects.requireNonNull(adapterRegistry, "adapterRegistry");
        this.secretService = Objects.requireNonNull(secretService, "secretService");
        this.requestIds = Objects.requireNonNull(requestIds, "requestIds");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(readOnly = true)
    public List<LoginConnectionResponse> list(String organizationId, String actorUserId) {
        authorize(organizationId, actorUserId, OrganizationAdministrativeAction.VIEW_LOGIN_CONNECTIONS);
        return queryRepository.findAll(organizationId);
    }

    @Transactional(readOnly = true)
    public LoginConnectionResponse get(
            String organizationId,
            String connectionId,
            String actorUserId
    ) {
        authorize(organizationId, actorUserId, OrganizationAdministrativeAction.VIEW_LOGIN_CONNECTIONS);
        return queryRepository.find(organizationId, connectionId);
    }

    @Transactional
    public LoginConnectionResponse create(
            String organizationId,
            LoginConnectionCreateRequest request,
            String actorUserId
    ) {
        authorize(organizationId, actorUserId, OrganizationAdministrativeAction.MANAGE_LOGIN_CONNECTIONS);
        LoginConnectionControlAdapter adapter = requireRegistry().require(
                new AdapterKey(request.adapterKey())
        );
        PreparedLoginConnectionRevision prepared = adapter.prepare(
                new LoginConnectionConfigurationInput(request.configuration())
        );
        Instant now = clock.instant();
        LoginConnection connection = connections.save(LoginConnection.createOrganization(
                organizationId,
                request.displayName(),
                adapter.adapterKey(),
                actorUserId,
                now
        ));
        Long secretVersion = null;
        if (prepared.secretPurpose().isPresent()) {
            authorize(organizationId, actorUserId, OrganizationAdministrativeAction.ROTATE_LOGIN_SECRETS);
            if (request.clientSecret() == null) {
                throw new DomainBadRequestException("error.loginConnection.secret.required");
            }
            secretVersion = rotateSecret(
                    organizationId,
                    connection.getId(),
                    prepared.secretPurpose().orElseThrow(),
                    request.clientSecret(),
                    actorUserId,
                    now
            );
        } else if (request.clientSecret() != null) {
            throw new DomainBadRequestException("error.loginConnection.secret.unsupported");
        }
        StoredLoginConnectionRevision revision = revisions.save(createRevision(
                connection,
                1,
                prepared,
                secretVersion,
                actorUserId,
                now
        ));
        recordAudit(
                organizationId,
                actorUserId,
                OrganizationAuditAction.LOGIN_CONNECTION_CREATED,
                OrganizationAuditTargetType.LOGIN_CONNECTION,
                connection.getId(),
                OrganizationAuditDetail.transition(null, LoginConnectionStatus.DRAFT.name()),
                false
        );
        recordAudit(
                organizationId,
                actorUserId,
                OrganizationAuditAction.LOGIN_CONNECTION_REVISION_CREATED,
                OrganizationAuditTargetType.LOGIN_CONNECTION_REVISION,
                revision.getId(),
                OrganizationAuditDetail.transition(null, Long.toString(revision.getRevision())),
                false
        );
        return queryRepository.find(organizationId, connection.getId());
    }

    @Transactional
    public LoginConnectionResponse createRevision(
            String organizationId,
            String connectionId,
            LoginConnectionRevisionCreateRequest request,
            String actorUserId
    ) {
        authorize(organizationId, actorUserId, OrganizationAdministrativeAction.MANAGE_LOGIN_CONNECTIONS);
        LoginConnection connection = requireConnection(organizationId, connectionId);
        LoginConnectionControlAdapter adapter = requireRegistry().require(connection.getAdapterKey());
        PreparedLoginConnectionRevision prepared = adapter.prepare(
                new LoginConnectionConfigurationInput(request.configuration())
        );
        StoredLoginConnectionRevision previous = revisions.findLatestByConnectionId(connectionId)
                .orElseThrow(() -> new DomainNotFoundException(
                        "error.loginConnection.revision.notFound"
                ));
        Instant now = clock.instant();
        Long secretVersion = previous.getSecretBindingVersion().orElse(null);
        if (request.clientSecret() != null) {
            authorize(organizationId, actorUserId, OrganizationAdministrativeAction.ROTATE_LOGIN_SECRETS);
            secretVersion = rotateSecret(
                    organizationId,
                    connectionId,
                    requiredSecretPurpose(prepared),
                    request.clientSecret(),
                    actorUserId,
                    now
            );
        }
        if (prepared.secretPurpose().isPresent() && secretVersion == null) {
            throw new DomainBadRequestException("error.loginConnection.secret.required");
        }
        StoredLoginConnectionRevision revision = revisions.save(createRevision(
                connection,
                previous.getRevision() + 1,
                prepared,
                secretVersion,
                actorUserId,
                now
        ));
        recordAudit(
                organizationId,
                actorUserId,
                OrganizationAuditAction.LOGIN_CONNECTION_REVISION_CREATED,
                OrganizationAuditTargetType.LOGIN_CONNECTION_REVISION,
                revision.getId(),
                OrganizationAuditDetail.transition(
                        Long.toString(previous.getRevision()),
                        Long.toString(revision.getRevision())
                ),
                false
        );
        return queryRepository.find(organizationId, connection.getId());
    }

    public LoginConnectionTestResponse testRevision(
            String organizationId,
            String connectionId,
            String revisionId,
            String actorUserId
    ) {
        authorize(organizationId, actorUserId, OrganizationAdministrativeAction.MANAGE_LOGIN_CONNECTIONS);
        LoginConnection connection = requireConnection(organizationId, connectionId);
        LoginConnectionControlAdapter adapter = requireRegistry().require(connection.getAdapterKey());
        Instant now = clock.instant();
        try {
            lifecycle.testRevision(
                    organizationId,
                    connectionId,
                    revisionId,
                    adapter.testProbe(),
                    now
            );
            operations.save(IdentityOperation.successfulLoginConnectionTest(
                    organizationId,
                    connectionId,
                    revisionId,
                    requestIds.current(),
                    now
            ));
            recordAudit(
                    organizationId,
                    actorUserId,
                    OrganizationAuditAction.LOGIN_CONNECTION_TEST_SUCCEEDED,
                    OrganizationAuditTargetType.LOGIN_CONNECTION_REVISION,
                    revisionId,
                    OrganizationAuditDetail.transition("UNTESTED", "SUCCEEDED"),
                    false
            );
            return new LoginConnectionTestResponse(true, revisionId, null, now);
        } catch (LoginConnectionTestException failed) {
            operations.save(IdentityOperation.failedLoginConnectionTest(
                    organizationId,
                    connectionId,
                    revisionId,
                    requestIds.current(),
                    failed.reason(),
                    now
            ));
            recordAudit(
                    organizationId,
                    actorUserId,
                    OrganizationAuditAction.LOGIN_CONNECTION_TEST_FAILED,
                    OrganizationAuditTargetType.LOGIN_CONNECTION_REVISION,
                    revisionId,
                    OrganizationAuditDetail.transition("UNTESTED", failed.reason().name()),
                    true
            );
            return new LoginConnectionTestResponse(false, revisionId, failed.reason().name(), now);
        }
    }

    @Transactional
    public LoginConnectionResponse activate(
            String organizationId,
            String connectionId,
            String revisionId,
            String actorUserId
    ) {
        authorize(organizationId, actorUserId, OrganizationAdministrativeAction.MANAGE_LOGIN_CONNECTIONS);
        String previousStatus = requireConnection(organizationId, connectionId).getStatus().name();
        lifecycle.activate(organizationId, connectionId, revisionId, clock.instant());
        LoginConnection connection = requireConnection(organizationId, connectionId);
        recordConnectionTransitionAudit(
                organizationId,
                actorUserId,
                OrganizationAuditAction.LOGIN_CONNECTION_ACTIVATED,
                previousStatus,
                connection
        );
        return queryRepository.find(organizationId, connection.getId());
    }

    @Transactional
    public LoginConnectionResponse suspend(
            String organizationId,
            String connectionId,
            String actorUserId
    ) {
        authorize(organizationId, actorUserId, OrganizationAdministrativeAction.MANAGE_LOGIN_CONNECTIONS);
        String previousStatus = requireConnection(organizationId, connectionId).getStatus().name();
        lifecycle.suspend(organizationId, connectionId, clock.instant());
        LoginConnection connection = requireConnection(organizationId, connectionId);
        recordConnectionTransitionAudit(
                organizationId,
                actorUserId,
                OrganizationAuditAction.LOGIN_CONNECTION_SUSPENDED,
                previousStatus,
                connection
        );
        return queryRepository.find(organizationId, connection.getId());
    }

    @Transactional
    public LoginConnectionResponse disable(
            String organizationId,
            String connectionId,
            String actorUserId
    ) {
        authorize(organizationId, actorUserId, OrganizationAdministrativeAction.MANAGE_LOGIN_CONNECTIONS);
        String previousStatus = requireConnection(organizationId, connectionId).getStatus().name();
        lifecycle.disable(organizationId, connectionId, clock.instant());
        LoginConnection connection = requireConnection(organizationId, connectionId);
        recordConnectionTransitionAudit(
                organizationId,
                actorUserId,
                OrganizationAuditAction.LOGIN_CONNECTION_DISABLED,
                previousStatus,
                connection
        );
        return queryRepository.find(organizationId, connection.getId());
    }

    private StoredLoginConnectionRevision createRevision(
            LoginConnection connection,
            long revisionNumber,
            PreparedLoginConnectionRevision prepared,
            Long secretVersion,
            String actorUserId,
            Instant now
    ) {
        if (!connection.getAdapterKey().equals(prepared.adapterDescriptor().adapterKey())) {
            throw new DomainBadRequestException("error.loginConnection.adapter.immutable");
        }
        var contract = prepared.adapterDescriptor().contractVersion();
        return StoredLoginConnectionRevision.create(
                connection.getId(),
                revisionNumber,
                contract.major() + "." + contract.minor(),
                prepared.adapterDescriptor().configSchemaVersion(),
                prepared.capabilitiesJson(),
                prepared.typedConfigJson(),
                secretVersion,
                actorUserId,
                now
        );
    }

    private long rotateSecret(
            String organizationId,
            String connectionId,
            SecretPurpose purpose,
            String secret,
            String actorUserId,
            Instant now
    ) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        try (SecretMaterial material = SecretMaterial.copyOf(bytes)) {
            return requireSecrets().rotate(
                    organizationId,
                    connectionId,
                    purpose,
                    material,
                    Duration.ZERO,
                    actorUserId,
                    now
            ).bindingVersion();
        } finally {
            java.util.Arrays.fill(bytes, (byte) 0);
        }
    }

    private static SecretPurpose requiredSecretPurpose(PreparedLoginConnectionRevision prepared) {
        return prepared.secretPurpose().orElseThrow(() -> new DomainBadRequestException(
                "error.loginConnection.secret.unsupported"
        ));
    }

    private LoginConnection requireConnection(String organizationId, String connectionId) {
        return connections.findByOrganizationIdAndId(organizationId, connectionId)
                .orElseThrow(() -> new DomainNotFoundException("error.loginConnection.notFound"));
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

    private static DomainBadRequestException unavailable() {
        return new DomainBadRequestException("error.loginConnection.controlPlane.unavailable");
    }

    private void authorize(
            String organizationId,
            String actorUserId,
            OrganizationAdministrativeAction action
    ) {
        authorization.requireAllowed(organizationId, actorUserId, action);
    }

    private void recordConnectionTransitionAudit(
            String organizationId,
            String actorUserId,
            OrganizationAuditAction action,
            String previousStatus,
            LoginConnection after
    ) {
        recordAudit(
                organizationId,
                actorUserId,
                action,
                OrganizationAuditTargetType.LOGIN_CONNECTION,
                after.getId(),
                OrganizationAuditDetail.transition(previousStatus, after.getStatus().name()),
                false
        );
    }

    private void recordAudit(
            String organizationId,
            String actorUserId,
            OrganizationAuditAction action,
            OrganizationAuditTargetType targetType,
            String targetReference,
            OrganizationAuditDetail detail,
            boolean failed
    ) {
        OrganizationAuditEvent event = failed
                ? OrganizationAuditEvent.failed(
                        actorUserId,
                        organizationId,
                        action,
                        targetType,
                        targetReference,
                        requestIds.current(),
                        detail
                )
                : OrganizationAuditEvent.success(
                        actorUserId,
                        organizationId,
                        action,
                        targetType,
                        targetReference,
                        requestIds.current(),
                        detail
                );
        auditLogService.record(event);
    }
}
