package com.iflytek.skillhub.service;

import static com.iflytek.skillhub.domain.organization.OrganizationAdministrativeAction.MANAGE_DIRECTORY_CONNECTIONS;
import static com.iflytek.skillhub.domain.organization.OrganizationAdministrativeAction.ROTATE_DIRECTORY_SECRETS;
import static com.iflytek.skillhub.domain.organization.OrganizationAdministrativeAction.VIEW_DIRECTORY_CONNECTIONS;

import com.iflytek.skillhub.config.DirectoryProvisioningProperties;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.organization.DirectoryConnection;
import com.iflytek.skillhub.domain.organization.DirectoryConnectionRepository;
import com.iflytek.skillhub.domain.organization.Organization;
import com.iflytek.skillhub.domain.organization.OrganizationAuthorizationService;
import com.iflytek.skillhub.domain.organization.OrganizationRepository;
import com.iflytek.skillhub.domain.organization.OrganizationStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.DirectoryConnectionResponse;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.iflytek.skillhub.domain.organization.DirectoryConnectionSecretHasher;

@Service
public class DirectoryConnectionAppService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final DirectoryConnectionRepository connections;
    private final OrganizationRepository organizations;
    private final OrganizationAuthorizationService authorization;
    private final DirectoryProvisioningProperties properties;
    private final AuditLogService audit;
    private final RequestIdAccessor requestIds;
    private final Clock clock;

    public DirectoryConnectionAppService(
            DirectoryConnectionRepository connections,
            OrganizationRepository organizations,
            OrganizationAuthorizationService authorization,
            DirectoryProvisioningProperties properties,
            AuditLogService audit,
            RequestIdAccessor requestIds,
            Clock clock
    ) {
        this.connections = connections;
        this.organizations = organizations;
        this.authorization = authorization;
        this.properties = properties;
        this.audit = audit;
        this.requestIds = requestIds;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DirectoryConnectionResponse> list(String organizationId, String actorId) {
        authorization.requireAllowed(organizationId, actorId, VIEW_DIRECTORY_CONNECTIONS);
        return connections.findByOrganizationId(organizationId).stream()
                .map(connection -> response(connection, null))
                .toList();
    }

    @Transactional
    public DirectoryConnectionResponse create(String organizationId, String actorId) {
        requireEnabled(organizationId);
        authorization.requireAllowed(organizationId, actorId, MANAGE_DIRECTORY_CONNECTIONS);
        requireActiveOrganization(organizationId);
        Instant now = clock.instant();
        String token = newToken();
        DirectoryConnection connection = connections.save(DirectoryConnection.create(
                organizationId, actorId, DirectoryConnectionSecretHasher.hash(token), hint(token), now));
        audit.recordOrganizationAdministration(
                actorId, "DIRECTORY_CONNECTION_CREATED", organizationId, connection.getId(), requestIds.current());
        return response(connection, token);
    }

    @Transactional
    public DirectoryConnectionResponse activate(String organizationId, String connectionId, String actorId) {
        requireEnabled(organizationId);
        authorization.requireAllowed(organizationId, actorId, MANAGE_DIRECTORY_CONNECTIONS);
        DirectoryConnection connection = lock(organizationId, connectionId);
        String before = connection.getStatus().name();
        connection.activate(clock.instant());
        DirectoryConnection saved = connections.save(connection);
        audit.recordOrganizationAdministration(
                actorId, "DIRECTORY_CONNECTION_ACTIVATED", organizationId, connectionId, requestIds.current());
        return response(saved, null);
    }

    @Transactional
    public DirectoryConnectionResponse suspend(String organizationId, String connectionId, String actorId) {
        requireEnabled(organizationId);
        authorization.requireAllowed(organizationId, actorId, MANAGE_DIRECTORY_CONNECTIONS);
        DirectoryConnection connection = lock(organizationId, connectionId);
        connection.suspend(clock.instant());
        DirectoryConnection saved = connections.save(connection);
        audit.recordOrganizationAdministration(
                actorId, "DIRECTORY_CONNECTION_SUSPENDED", organizationId, connectionId, requestIds.current());
        return response(saved, null);
    }

    @Transactional
    public DirectoryConnectionResponse disable(String organizationId, String connectionId, String actorId) {
        requireEnabled(organizationId);
        authorization.requireAllowed(organizationId, actorId, MANAGE_DIRECTORY_CONNECTIONS);
        DirectoryConnection connection = lock(organizationId, connectionId);
        connection.disable(clock.instant());
        DirectoryConnection saved = connections.save(connection);
        audit.recordOrganizationAdministration(
                actorId, "DIRECTORY_CONNECTION_DISABLED", organizationId, connectionId, requestIds.current());
        return response(saved, null);
    }

    @Transactional
    public DirectoryConnectionResponse rotateSecret(String organizationId, String connectionId, String actorId) {
        requireEnabled(organizationId);
        authorization.requireAllowed(organizationId, actorId, ROTATE_DIRECTORY_SECRETS);
        DirectoryConnection connection = lock(organizationId, connectionId);
        String token = newToken();
        connection.rotateSecret(DirectoryConnectionSecretHasher.hash(token), hint(token), clock.instant());
        DirectoryConnection saved = connections.save(connection);
        audit.recordOrganizationAdministration(
                actorId, "DIRECTORY_CONNECTION_SECRET_ROTATED", organizationId, connectionId, requestIds.current());
        return response(saved, token);
    }

    @Transactional(readOnly = true)
    public DirectoryConnection requireActiveScimConnection(String organizationId, String connectionId) {
        if (!properties.allows(organizationId)) {
            throw new DomainConflictException("error.directory.provisioning.disabled");
        }
        DirectoryConnection connection = connections.findByOrganizationIdAndId(organizationId, connectionId)
                .orElseThrow(() -> new DomainNotFoundException("error.directory.connection.not-found"));
        if (connection.getStatus() != com.iflytek.skillhub.domain.organization.DirectoryConnectionStatus.ACTIVE) {
            throw new DomainConflictException("error.directory.connection.inactive");
        }
        return connection;
    }

    @Transactional(readOnly = true)
    public DirectoryConnection requireConnection(String organizationId, String connectionId) {
        return connections.findByOrganizationIdAndId(organizationId, connectionId)
                .orElseThrow(() -> new DomainNotFoundException("error.directory.connection.not-found"));
    }

    private DirectoryConnection lock(String organizationId, String connectionId) {
        return connections.findByOrganizationIdAndIdForUpdate(organizationId, connectionId)
                .orElseThrow(() -> new DomainNotFoundException("error.directory.connection.not-found"));
    }

    private void requireEnabled(String organizationId) {
        if (!properties.allows(organizationId)) {
            throw new DomainConflictException("error.directory.provisioning.disabled");
        }
    }

    private void requireActiveOrganization(String organizationId) {
        Organization organization = organizations.findById(organizationId)
                .orElseThrow(() -> new DomainNotFoundException("error.organization.not-found"));
        if (organization.getStatus() != OrganizationStatus.ACTIVE) {
            throw new DomainConflictException("error.organization.inactive");
        }
    }

    private DirectoryConnectionResponse response(DirectoryConnection connection, String token) {
        String base = "/api/v1/organizations/" + connection.getOrganizationId()
                + "/directory-connections/" + connection.getId() + "/scim/v2";
        return new DirectoryConnectionResponse(
                connection.getId(), connection.getOrganizationId(), connection.getProtocol().name(),
                connection.getStatus().name(), connection.getSecretVersion(), connection.getTokenHint(),
                base, connection.getCreatedAt(), connection.getUpdatedAt(), token);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "sh_scim_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hint(String token) {
        return token.substring(Math.max(0, token.length() - 8));
    }

}
