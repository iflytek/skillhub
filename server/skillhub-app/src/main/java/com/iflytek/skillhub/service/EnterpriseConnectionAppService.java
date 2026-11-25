package com.iflytek.skillhub.service;

import static com.iflytek.skillhub.domain.organization.OrganizationAdministrativeAction.MANAGE_LOGIN_CONNECTIONS;
import static com.iflytek.skillhub.domain.organization.OrganizationAdministrativeAction.ROTATE_LOGIN_SECRETS;
import static com.iflytek.skillhub.domain.organization.OrganizationAdministrativeAction.VIEW_LOGIN_CONNECTIONS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.connection.oidc.EnterpriseOidcProperties;
import com.iflytek.skillhub.auth.connection.oidc.OidcConnectionConfiguration;
import com.iflytek.skillhub.auth.connection.oidc.OidcConnectionProbe;
import com.iflytek.skillhub.auth.connection.persistence.LoginConnection;
import com.iflytek.skillhub.auth.connection.persistence.LoginConnectionRepository;
import com.iflytek.skillhub.auth.connection.persistence.LoginConnectionRevision;
import com.iflytek.skillhub.auth.connection.persistence.LoginConnectionRevisionRepository;
import com.iflytek.skillhub.auth.connection.persistence.LoginConnectionStatus;
import com.iflytek.skillhub.auth.connection.secret.LoginConnectionSecretService;
import com.iflytek.skillhub.auth.federation.adapter.RemoteIdentityIoExecutor;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.organization.OrganizationAdministrativeAction;
import com.iflytek.skillhub.domain.organization.OrganizationAuthorizationService;
import com.iflytek.skillhub.domain.organization.OrganizationRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.LoginConnectionCreateRequest;
import com.iflytek.skillhub.dto.LoginConnectionManagementResponse;
import com.iflytek.skillhub.dto.LoginConnectionResponse;
import com.iflytek.skillhub.dto.LoginConnectionResponse.LoginConnectionHealthResponse;
import com.iflytek.skillhub.dto.LoginConnectionResponse.LoginConnectionRevisionResponse;
import com.iflytek.skillhub.dto.LoginConnectionRevisionCreateRequest;
import com.iflytek.skillhub.dto.LoginConnectionSecretRequest;
import com.iflytek.skillhub.dto.LoginConnectionTestResponse;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.LoginConnectionProbeRepository;
import com.iflytek.skillhub.repository.LoginConnectionProbeRepository.ProbeOutcome;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class EnterpriseConnectionAppService {
    private final OrganizationAuthorizationService authorization;
    private final OrganizationRepository organizations;
    private final LoginConnectionRepository connections;
    private final LoginConnectionRevisionRepository revisions;
    private final LoginConnectionSecretService secrets;
    private final LoginConnectionProbeRepository probes;
    private final OidcConnectionProbe oidcProbe;
    private final EnterpriseOidcProperties settings;
    private final RemoteIdentityIoExecutor remoteIo;
    private final AuditLogService audit;
    private final RequestIdAccessor requestIds;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public EnterpriseConnectionAppService(
            OrganizationAuthorizationService authorization, OrganizationRepository organizations,
            LoginConnectionRepository connections, LoginConnectionRevisionRepository revisions,
            LoginConnectionSecretService secrets, LoginConnectionProbeRepository probes,
            OidcConnectionProbe oidcProbe, EnterpriseOidcProperties settings, RemoteIdentityIoExecutor remoteIo,
            AuditLogService audit, RequestIdAccessor requestIds, ObjectMapper mapper, Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.authorization = authorization;
        this.organizations = organizations;
        this.connections = connections;
        this.revisions = revisions;
        this.secrets = secrets;
        this.probes = probes;
        this.oidcProbe = oidcProbe;
        this.settings = settings;
        this.remoteIo = remoteIo;
        this.audit = audit;
        this.requestIds = requestIds;
        this.mapper = mapper;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public LoginConnectionManagementResponse management(String organizationId, String actorId) {
        authorization.requireAllowed(organizationId, actorId, VIEW_LOGIN_CONNECTIONS);
        return new LoginConnectionManagementResponse(settings.isEnabled());
    }

    @Transactional(readOnly = true)
    public LoginConnectionResponse get(String organizationId, String connectionId, String actorId) {
        authorization.requireAllowed(organizationId, actorId, VIEW_LOGIN_CONNECTIONS);
        LoginConnection connection = requireOrganizationOidcConnection(organizationId, connectionId);
        return response(connection, latestRevision(connectionId));
    }

    @Transactional
    public LoginConnectionResponse create(String organizationId, LoginConnectionCreateRequest request, String actorId) {
        requireMutationAuthority(organizationId, actorId, MANAGE_LOGIN_CONNECTIONS);
        OidcConnectionConfiguration configuration = new OidcConnectionConfiguration(request.issuer(), request.clientId());
        Instant now = clock.instant();
        LoginConnection connection = connections.save(LoginConnection.createOrganization(
                organizationId, request.displayName(), actorId, now));
        LoginConnectionRevision revision = revisions.save(LoginConnectionRevision.create(
                connection.getId(), 1, configuration, null, actorId, now));
        recordAudit(connection, actorId, "LOGIN_CONNECTION_CREATED", revision.getId(), null, "DRAFT", true);
        return response(connection, revision);
    }

    @Transactional
    public LoginConnectionResponse createRevision(
            String organizationId, String connectionId, LoginConnectionRevisionCreateRequest request, String actorId
    ) {
        var connection = lockConnection(organizationId, connectionId, actorId, MANAGE_LOGIN_CONNECTIONS);
        requireEditable(connection);
        var previous = requireLatestRevision(connectionId, request.expectedRevisionId());
        var configuration = new OidcConnectionConfiguration(request.issuer(), request.clientId());
        if (configuration.equals(previous.getConfiguration())) {
            return response(connection, previous);
        }
        var revision = revisions.save(LoginConnectionRevision.create(
                connectionId, previous.getRevision() + 1, configuration, null, actorId, clock.instant()));
        recordAudit(connection, actorId, "LOGIN_CONNECTION_REVISION_CREATED",
                revision.getId(), previous.getId(), revision.getId(), true);
        return response(connection, revision);
    }

    @Transactional
    public LoginConnectionResponse writeSecret(
            String organizationId, String connectionId, String revisionId, LoginConnectionSecretRequest request,
            String actorId
    ) {
        LoginConnection connection = lockConnection(organizationId, connectionId, actorId, ROTATE_LOGIN_SECRETS);
        requireEditable(connection);
        LoginConnectionRevision previous = requireLatestRevision(connectionId, revisionId);
        Instant now = clock.instant();
        byte[] plaintext = request.clientSecret().getBytes(StandardCharsets.UTF_8);
        long version;
        try {
            version = secrets.write(organizationId, connectionId, actorId, plaintext, now);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
        var revision = revisions.save(LoginConnectionRevision.create(
                connectionId, previous.getRevision() + 1, previous.getConfiguration(), version, actorId, now));
        recordAudit(connection, actorId, "LOGIN_CONNECTION_SECRET_WRITTEN", revision.getId(),
                previous.getId(), revision.getId(), true);
        return response(connection, revision);
    }

    public LoginConnectionTestResponse testRevision(
            String organizationId, String connectionId, String revisionId, String actorId
    ) {
        var configuration = transactions.execute(transaction -> {
            authorization.requireAllowed(organizationId, actorId, MANAGE_LOGIN_CONNECTIONS);
            requireEnabled();
            requireEditable(requireOrganizationOidcConnection(organizationId, connectionId));
            var revision = requireLatestRevision(connectionId, revisionId);
            requireCredential(revision);
            return revision.getConfiguration();
        });
        Instant started = clock.instant();
        String failure = remoteIo.execute(() -> {
            try {
                oidcProbe.verify(configuration);
                return null;
            } catch (OidcConnectionProbe.Failure failed) {
                return failed.getReason().name();
            }
        });
        return transactions.execute(transaction -> {
            LoginConnection connection = lockConnection(organizationId, connectionId, actorId, MANAGE_LOGIN_CONNECTIONS);
            requireEditable(connection);
            requireCredential(requireLatestRevision(connectionId, revisionId));
            String before = probes.find(organizationId, connectionId, revisionId)
                    .map(ProbeOutcome::status).orElse("UNTESTED");
            Instant checkedAt = clock.instant();
            boolean success = failure == null;
            if (success) {
                connection.recordSuccessfulTest(revisionId, checkedAt);
            } else {
                connection.recordFailedTest(revisionId, checkedAt);
            }
            String result = success ? "SUCCEEDED" : "FAILED";
            probes.record(organizationId, connectionId, revisionId, requestIds.current(), started,
                    new ProbeOutcome(result, failure, checkedAt));
            recordAudit(connection, actorId, "LOGIN_CONNECTION_TESTED", revisionId, before, result, success);
            return new LoginConnectionTestResponse(success, revisionId, failure, checkedAt);
        });
    }

    @Transactional
    public LoginConnectionResponse activate(String organizationId, String connectionId, String revisionId, String actorId) {
        LoginConnection connection = lockConnection(organizationId, connectionId, actorId, MANAGE_LOGIN_CONNECTIONS);
        requireEditable(connection);
        LoginConnectionRevision revision = requireLatestRevision(connectionId, revisionId);
        requireCredential(revision);
        if (!revisionId.equals(connection.getLastTestedRevisionId())
                || probes.find(organizationId, connectionId, revisionId).filter(ProbeOutcome::succeeded).isEmpty()) {
            throw new DomainConflictException("error.loginConnection.revision.notTested");
        }
        if (connection.getStatus() != LoginConnectionStatus.ACTIVE || !revisionId.equals(connection.getActiveRevisionId())) {
            String before = connection.getStatus().name();
            connection.activate(revisionId, clock.instant());
            recordAudit(connection, actorId, "LOGIN_CONNECTION_ACTIVATED", revisionId,
                    before, connection.getStatus().name(), true);
        }
        return response(connection, revision);
    }

    @Transactional
    public LoginConnectionResponse suspend(String organizationId, String connectionId, String actorId) {
        var connection = lockConnection(organizationId, connectionId, actorId, MANAGE_LOGIN_CONNECTIONS);
        if (connection.getStatus() != LoginConnectionStatus.SUSPENDED) {
            String before = connection.getStatus().name();
            connection.suspend(clock.instant());
            recordAudit(connection, actorId, "LOGIN_CONNECTION_SUSPENDED", connection.getActiveRevisionId(),
                    before, connection.getStatus().name(), true);
        }
        return response(connection, latestRevision(connectionId));
    }

    @Transactional
    public LoginConnectionResponse resume(String organizationId, String connectionId, String revisionId, String actorId) {
        var connection = lockConnection(organizationId, connectionId, actorId, MANAGE_LOGIN_CONNECTIONS);
        if (connection.resume(revisionId, clock.instant())) {
            recordAudit(connection, actorId, "LOGIN_CONNECTION_RESUMED", revisionId,
                    LoginConnectionStatus.SUSPENDED.name(), LoginConnectionStatus.ACTIVE.name(), true);
        }
        return response(connection, latestRevision(connectionId));
    }

    @Transactional
    public LoginConnectionResponse disable(String organizationId, String connectionId, String actorId) {
        var connection = lockConnection(organizationId, connectionId, actorId, MANAGE_LOGIN_CONNECTIONS);
        if (connection.getStatus() != LoginConnectionStatus.DISABLED) {
            String before = connection.getStatus().name();
            String activeRevisionId = connection.getActiveRevisionId();
            connection.disable(clock.instant());
            recordAudit(connection, actorId, "LOGIN_CONNECTION_DISABLED", activeRevisionId,
                    before, connection.getStatus().name(), true);
        }
        return response(connection, latestRevision(connectionId));
    }

    private LoginConnection lockConnection(
            String organizationId, String connectionId, String actorId, OrganizationAdministrativeAction action
    ) {
        requireMutationAuthority(organizationId, actorId, action);
        return connections.lockByOrganizationIdAndId(organizationId, connectionId)
                .filter(connection -> LoginConnection.OIDC_ADAPTER_KEY.equals(connection.getAdapterKey()))
                .orElseThrow(EnterpriseConnectionAppService::notFound);
    }

    private LoginConnection requireOrganizationOidcConnection(String organizationId, String connectionId) {
        return connections.findByOrganizationIdAndId(organizationId, connectionId)
                .filter(connection -> LoginConnection.OIDC_ADAPTER_KEY.equals(connection.getAdapterKey()))
                .orElseThrow(EnterpriseConnectionAppService::notFound);
    }

    private void requireMutationAuthority(String organizationId, String actorId, OrganizationAdministrativeAction action) {
        // Lock in the same order as organization membership/role writes before reading the caller's authority.
        organizations.findByIdForWrite(organizationId)
                .orElseThrow(() -> new DomainForbiddenException("error.organization.permission.denied"));
        authorization.requireAllowed(organizationId, actorId, action);
        requireEnabled();
    }

    private void requireEnabled() {
        if (!settings.isEnabled()) {
            throw new DomainNotFoundException("error.loginConnection.management.disabled");
        }
    }

    private LoginConnectionRevision latestRevision(String connectionId) {
        return revisions.findFirstByConnectionIdOrderByRevisionDesc(connectionId)
                .orElseThrow(() -> new DomainNotFoundException("error.loginConnection.revision.notFound"));
    }

    private LoginConnectionRevision requireLatestRevision(String connectionId, String revisionId) {
        LoginConnectionRevision revision = latestRevision(connectionId);
        if (!revision.getId().equals(revisionId)) {
            throw new DomainConflictException("error.loginConnection.revision.changed");
        }
        return revision;
    }

    private static void requireEditable(LoginConnection connection) {
        if (connection.getStatus() == LoginConnectionStatus.DISABLED) {
            throw new DomainConflictException("error.loginConnection.status.invalid");
        }
    }

    private static void requireCredential(LoginConnectionRevision revision) {
        if (revision.getSecretBindingVersion() == null) {
            throw new DomainConflictException("error.loginConnection.secret.required");
        }
    }

    private static DomainNotFoundException notFound() {
        return new DomainNotFoundException("error.loginConnection.notFound");
    }

    private LoginConnectionResponse response(LoginConnection connection, LoginConnectionRevision revision) {
        var health = probes.find(connection.getOrganizationId(), connection.getId(), revision.getId())
                .map(result -> new LoginConnectionHealthResponse(
                        result.status(), result.failureReason(), result.checkedAt()))
                .orElseGet(() -> new LoginConnectionHealthResponse("UNTESTED", null, null));
        var latest = revisionResponse(revision);
        String activeId = connection.getActiveRevisionId();
        LoginConnectionRevisionResponse active = null;
        if (activeId != null) {
            active = activeId.equals(revision.getId()) ? latest
                    : revisions.findByConnectionIdAndId(connection.getId(), activeId)
                            .map(EnterpriseConnectionAppService::revisionResponse)
                            .orElseThrow(() -> new DomainNotFoundException("error.loginConnection.revision.notFound"));
        }
        return new LoginConnectionResponse(connection.getId(), connection.getDisplayName(),
                connection.getStatus().name(), activeId, connection.getLastTestedRevisionId(),
                active, latest, health, connection.getCreatedAt(), connection.getUpdatedAt());
    }

    private static LoginConnectionRevisionResponse revisionResponse(LoginConnectionRevision revision) {
        var configuration = revision.getConfiguration();
        return new LoginConnectionRevisionResponse(revision.getId(), revision.getRevision(),
                configuration.issuer(), configuration.clientId(), revision.getSecretBindingVersion() != null,
                revision.getCreatedAt());
    }

    private void recordAudit(
            LoginConnection connection, String actorId, String action, String revisionId,
            String from, String to, boolean success
    ) {
        var detail = mapper.createObjectNode().put("revisionId", revisionId).put("from", from).put("to", to);
        audit.recordOrganizationLoginConnection(actorId, action, connection.getOrganizationId(),
                connection.getId(), requestIds.current(), success, detail.toString());
    }
}
