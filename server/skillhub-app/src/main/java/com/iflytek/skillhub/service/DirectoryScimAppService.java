package com.iflytek.skillhub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.organization.DirectoryConnection;
import com.iflytek.skillhub.domain.organization.MembershipSourceType;
import com.iflytek.skillhub.domain.organization.OrganizationMembership;
import com.iflytek.skillhub.domain.organization.OrganizationMembershipRepository;
import com.iflytek.skillhub.domain.organization.OrganizationMembershipStatus;
import com.iflytek.skillhub.dto.ScimListResponse;
import com.iflytek.skillhub.dto.ScimUserRequest;
import com.iflytek.skillhub.dto.ScimUserResource;
import com.iflytek.skillhub.exception.ScimProtocolException;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DirectoryScimAppService {

    private static final String USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";
    private static final String ENTERPRISE_SCHEMA = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";
    private static final Pattern FILTER = Pattern.compile(
            "^(externalId|userName)\\s+eq\\s+\"([^\"]{1,256})\"$", Pattern.CASE_INSENSITIVE);
    private static final int MAX_COUNT = 100;
    private static final int MAX_START_INDEX = 10_000;

    private final DirectoryConnectionAppService connections;
    private final OrganizationMembershipRepository memberships;
    private final AuditLogService audit;
    private final RequestIdAccessor requestIds;
    private final Clock clock;

    public DirectoryScimAppService(
            DirectoryConnectionAppService connections,
            OrganizationMembershipRepository memberships,
            AuditLogService audit,
            RequestIdAccessor requestIds,
            Clock clock
    ) {
        this.connections = connections;
        this.memberships = memberships;
        this.audit = audit;
        this.requestIds = requestIds;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ScimListResponse list(
            String organizationId, String connectionId, int startIndex, int count, String filter
    ) {
        DirectoryConnection connection = requireConnection(organizationId, connectionId);
        if (startIndex < 1 || startIndex > MAX_START_INDEX || count < 1 || count > MAX_COUNT) {
            throw badRequest("invalidValue", "Invalid SCIM pagination");
        }
        Filter parsed = parseFilter(filter);
        List<OrganizationMembership> content;
        int total;
        if (parsed != null && parsed.field().equalsIgnoreCase("externalId")) {
            String sourceId = sourceId(connectionId, parsed.value());
            content = memberships.findByOrganizationIdAndSource(
                            organizationId, MembershipSourceType.DIRECTORY, sourceId)
                    .filter(member -> member.getStatus() != OrganizationMembershipStatus.DEPROVISIONED)
                    .map(List::of)
                    .orElseGet(List::of);
            total = content.size();
        } else {
            Page<OrganizationMembership> page = memberships.findDirectoryMembers(
                    organizationId,
                    connectionId,
                    parsed != null && parsed.field().equalsIgnoreCase("userName") ? parsed.value() : null,
                    null,
                    PageRequest.of(0, Math.min(MAX_START_INDEX, startIndex - 1 + count),
                            Sort.by(Sort.Direction.ASC, "id")));
            total = Math.toIntExact(page.getTotalElements());
            int from = Math.min(startIndex - 1, page.getContent().size());
            int to = Math.min(from + count, page.getContent().size());
            content = page.getContent().subList(from, to);
        }
        return new ScimListResponse(
                List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse"),
                total,
                startIndex,
                content.size(),
                content.stream().map(member -> resource(connection, member)).toList());
    }

    @Transactional(readOnly = true)
    public ScimUserResource get(String organizationId, String connectionId, String membershipId) {
        DirectoryConnection connection = requireConnection(organizationId, connectionId);
        OrganizationMembership membership = memberships.findByOrganizationIdAndId(organizationId, membershipId)
                .orElseThrow(() -> notFound(membershipId));
        requireOwnedByConnection(connection, membership);
        return resource(connection, membership);
    }

    @Transactional
    public ScimUserResource create(String organizationId, String connectionId, ScimUserRequest request) {
        DirectoryConnection connection = requireConnection(organizationId, connectionId);
        Profile profile = profile(request, true);
        String sourceId = sourceId(connectionId, profile.externalId());
        OrganizationMembership membership = memberships.findByOrganizationIdAndSource(
                        organizationId, MembershipSourceType.DIRECTORY, sourceId)
                .orElse(null);
        Instant now = clock.instant();
        if (membership == null) {
            membership = OrganizationMembership.directoryProvisioned(
                    organizationId, sourceId, profile.userName(), profile.displayName(), profile.email(),
                    profile.department(), profile.employeeNumber(), now);
        } else {
            membership.restoreDirectory(now);
            membership.updateDirectoryProfile(
                    profile.userName(), profile.displayName(), profile.email(),
                    profile.department(), profile.employeeNumber(), now);
        }
        membership.applyDirectoryActive(profile.active(), now);
        OrganizationMembership saved = memberships.save(membership);
        audit.recordOrganizationAdministration(
                "scim:" + connectionId, "DIRECTORY_USER_PROVISIONED", organizationId,
                saved.getId(), requestIds.current());
        return resource(connection, saved);
    }

    @Transactional
    public ScimUserResource replace(
            String organizationId, String connectionId, String membershipId, ScimUserRequest request
    ) {
        DirectoryConnection connection = requireConnection(organizationId, connectionId);
        OrganizationMembership membership = ownedMembership(organizationId, connection, membershipId);
        Profile profile = profile(request, true);
        if (!sourceId(connectionId, profile.externalId()).equals(membership.getSourceId())) {
            throw conflict("externalId cannot change for an existing SCIM resource");
        }
        Instant now = clock.instant();
        membership.updateDirectoryProfile(
                profile.userName(), profile.displayName(), profile.email(),
                profile.department(), profile.employeeNumber(), now);
        membership.applyDirectoryActive(profile.active(), now);
        return resource(connection, memberships.save(membership));
    }

    @Transactional
    public ScimUserResource patch(
            String organizationId, String connectionId, String membershipId, JsonNode body
    ) {
        DirectoryConnection connection = requireConnection(organizationId, connectionId);
        OrganizationMembership membership = ownedMembership(organizationId, connection, membershipId);
        JsonNode operations = body == null ? null : body.get("Operations");
        if (operations == null || !operations.isArray() || operations.size() > 32) {
            throw badRequest("invalidValue", "SCIM PATCH Operations must be a non-empty array");
        }
        String userName = membership.getSourceUserName();
        String displayName = membership.getDisplayName();
        String email = membership.getPrimaryEmail();
        String department = membership.getDepartment();
        String employeeNumber = membership.getEmployeeNumber();
        Boolean active = membership.getStatus() != OrganizationMembershipStatus.SUSPENDED
                && membership.getStatus() != OrganizationMembershipStatus.DEPROVISIONED;
        for (JsonNode operation : operations) {
            String rawOp = text(operation, "op");
            if (rawOp == null || rawOp.isBlank()) {
                throw badRequest("invalidSyntax", "SCIM PATCH operation is required");
            }
            String op = rawOp.toUpperCase(Locale.ROOT);
            String path = text(operation, "path");
            JsonNode value = operation.get("value");
            if (path == null || path.isBlank()) {
                throw badRequest("invalidPath", "SCIM PATCH path is required");
            }
            switch (path) {
                case "active" -> active = value != null && value.asBoolean();
                case "userName" -> userName = value == null ? null : value.asText();
                case "displayName" -> displayName = value == null ? null : value.asText();
                case "emails" -> email = firstEmail(value);
                case "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:department" ->
                        department = value == null ? null : value.asText();
                case "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:employeeNumber" ->
                        employeeNumber = value == null ? null : value.asText();
                default -> throw badRequest("noTarget", "Unsupported SCIM PATCH path");
            }
            if (!(op.equals("ADD") || op.equals("REPLACE") || op.equals("REMOVE"))) {
                throw badRequest("invalidSyntax", "Unsupported SCIM PATCH operation");
            }
        }
        Instant now = clock.instant();
        membership.updateDirectoryProfile(userName, displayName, email, department, employeeNumber, now);
        membership.applyDirectoryActive(active, now);
        return resource(connection, memberships.save(membership));
    }

    @Transactional
    public void delete(String organizationId, String connectionId, String membershipId) {
        DirectoryConnection connection = requireConnection(organizationId, connectionId);
        OrganizationMembership membership = ownedMembership(organizationId, connection, membershipId);
        membership.deprovision(clock.instant());
        memberships.save(membership);
        audit.recordOrganizationAdministration(
                "scim:" + connectionId, "DIRECTORY_USER_DEPROVISIONED", organizationId,
                membershipId, requestIds.current());
    }

    private DirectoryConnection requireConnection(String organizationId, String connectionId) {
        try {
            return connections.requireActiveScimConnection(organizationId, connectionId);
        } catch (RuntimeException failure) {
            throw new ScimProtocolException(HttpStatus.FORBIDDEN, "Directory connection is unavailable");
        }
    }

    private OrganizationMembership ownedMembership(
            String organizationId, DirectoryConnection connection, String membershipId
    ) {
        OrganizationMembership membership = memberships.findByOrganizationIdAndId(organizationId, membershipId)
                .orElseThrow(() -> notFound(membershipId));
        requireOwnedByConnection(connection, membership);
        if (membership.getStatus() == OrganizationMembershipStatus.DEPROVISIONED) {
            throw notFound(membershipId);
        }
        return membership;
    }

    private void requireOwnedByConnection(DirectoryConnection connection, OrganizationMembership membership) {
        if (membership.getSourceType() != MembershipSourceType.DIRECTORY
                || !membership.getSourceId().startsWith(connection.getId() + ":")) {
            throw notFound(membership.getId());
        }
    }

    private ScimUserResource resource(DirectoryConnection connection, OrganizationMembership membership) {
        String externalId = membership.getSourceId().substring((connection.getId() + ":").length());
        String name = membership.getDisplayName() == null ? "" : membership.getDisplayName().trim();
        String[] parts = name.split("\\s+", 2);
        String givenName = parts.length > 0 ? parts[0] : "";
        String familyName = parts.length > 1 ? parts[1] : "";
        boolean active = membership.getStatus() != OrganizationMembershipStatus.SUSPENDED
                && membership.getStatus() != OrganizationMembershipStatus.DEPROVISIONED;
        return new ScimUserResource(
                List.of(USER_SCHEMA, ENTERPRISE_SCHEMA),
                membership.getId(), externalId, membership.getSourceUserName(),
                membership.getDisplayName(), active,
                new ScimUserResource.ScimName(givenName, familyName),
                membership.getPrimaryEmail() == null ? List.of() : List.of(
                        new ScimUserResource.ScimEmail(membership.getPrimaryEmail(), "work", true)),
                new ScimUserResource.ScimEnterpriseUser(
                        membership.getDepartment(), membership.getEmployeeNumber()),
                new ScimUserResource.ScimMeta(
                        "User", membership.getCreatedAt().toString(), membership.getUpdatedAt().toString(),
                        "/api/v1/organizations/" + connection.getOrganizationId()
                                + "/directory-connections/" + connection.getId() + "/scim/v2/Users/"
                                + membership.getId(), membership.getUpdatedAt().toString()));
    }

    private Profile profile(ScimUserRequest request, boolean requireIdentity) {
        if (request == null) {
            throw badRequest("invalidValue", "SCIM User body is required");
        }
        String externalId = trim(request.externalId());
        String userName = trim(request.userName());
        if (requireIdentity && (externalId == null || userName == null)) {
            throw badRequest("invalidValue", "SCIM externalId and userName are required");
        }
        String email = request.emails() == null || request.emails().isEmpty()
                ? null : trim(request.emails().get(0).value());
        String displayName = trim(request.displayName());
        if (displayName == null && request.name() != null) {
            displayName = trim((request.name().givenName() == null ? "" : request.name().givenName())
                    + " " + (request.name().familyName() == null ? "" : request.name().familyName()));
        }
        var enterprise = request.enterprise();
        return new Profile(
                externalId,
                userName,
                displayName,
                email,
                enterprise == null ? null : trim(enterprise.department()),
                enterprise == null ? null : trim(enterprise.employeeNumber()),
                request.active() == null || request.active());
    }

    private Filter parseFilter(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher matcher = FILTER.matcher(raw.trim());
        if (!matcher.matches()) {
            throw badRequest("invalidFilter", "Only externalId eq \"...\" and userName eq \"...\" are supported");
        }
        return new Filter(matcher.group(1), matcher.group(2));
    }

    private String firstEmail(JsonNode value) {
        if (value == null || !value.isArray() || value.isEmpty()) {
            return null;
        }
        return value.get(0).path("value").asText(null);
    }

    private String text(JsonNode object, String field) {
        JsonNode value = object.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String trim(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String sourceId(String connectionId, String externalId) {
        return connectionId + ":" + externalId;
    }

    private ScimProtocolException badRequest(String type, String detail) {
        return new ScimProtocolException(HttpStatus.BAD_REQUEST, type, detail);
    }

    private ScimProtocolException conflict(String detail) {
        return new ScimProtocolException(HttpStatus.CONFLICT, "uniqueness", detail);
    }

    private ScimProtocolException notFound(String id) {
        return new ScimProtocolException(HttpStatus.NOT_FOUND, "Resource not found: " + id);
    }

    private record Filter(String field, String value) {}

    private record Profile(
            String externalId,
            String userName,
            String displayName,
            String email,
            String department,
            String employeeNumber,
            boolean active
    ) {}
}
