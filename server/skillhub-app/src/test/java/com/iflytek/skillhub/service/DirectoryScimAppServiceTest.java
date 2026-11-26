package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.organization.DirectoryConnection;
import com.iflytek.skillhub.domain.organization.MembershipSourceType;
import com.iflytek.skillhub.domain.organization.OrganizationMembership;
import com.iflytek.skillhub.domain.organization.OrganizationMembershipRepository;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DirectoryScimAppServiceTest {

    private final DirectoryConnectionAppService connections = mock(DirectoryConnectionAppService.class);
    private final OrganizationMembershipRepository memberships = mock(OrganizationMembershipRepository.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final RequestIdAccessor requestIds = mock(RequestIdAccessor.class);
    private final DirectoryScimAppService service = new DirectoryScimAppService(
            connections, memberships, audit, requestIds,
            Clock.fixed(Instant.parse("2026-11-26T00:00:00Z"), ZoneOffset.UTC));

    private DirectoryConnection connection;

    @BeforeEach
    void setUp() {
        connection = DirectoryConnection.create(
                "org-1", "admin", "a".repeat(64), "token1234",
                Instant.parse("2026-11-26T00:00:00Z"));
        connection.activate(Instant.parse("2026-11-26T00:00:01Z"));
        when(connections.requireActiveScimConnection("org-1", connection.getId())).thenReturn(connection);
        when(requestIds.current()).thenReturn("request-1");
    }

    @Test
    void createIsIdempotentForSameExternalResource() {
        when(memberships.findByOrganizationIdAndSource(
                eq("org-1"), eq(MembershipSourceType.DIRECTORY), any(String.class)))
                .thenReturn(Optional.empty());
        when(memberships.save(any(OrganizationMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var first = service.create("org-1", connection.getId(), request("external-1", "alice"));

        assertThat(first.externalId()).isEqualTo("external-1");
        assertThat(first.userName()).isEqualTo("alice");
        verify(memberships).save(any(OrganizationMembership.class));
    }

    @Test
    void deleteDeprovisionsDirectoryMembership() {
        OrganizationMembership membership = OrganizationMembership.directoryProvisioned(
                "org-1", connection.getId() + ":external-1", "alice", "Alice", "alice@example.com",
                null, null, Instant.parse("2026-11-26T00:00:00Z"));
        when(memberships.findByOrganizationIdAndId("org-1", membership.getId()))
                .thenReturn(Optional.of(membership));
        when(memberships.save(any(OrganizationMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.delete("org-1", connection.getId(), membership.getId());

        assertThat(membership.getStatus().name()).isEqualTo("DEPROVISIONED");
        verify(audit).recordOrganizationAdministration(
                eq("scim:" + connection.getId()), eq("DIRECTORY_USER_DEPROVISIONED"),
                eq("org-1"), eq(membership.getId()), eq("request-1"));
    }

    private com.iflytek.skillhub.dto.ScimUserRequest request(String externalId, String userName) {
        return new com.iflytek.skillhub.dto.ScimUserRequest(
                null, externalId, userName, "Alice", true, null,
                java.util.List.of(new com.iflytek.skillhub.dto.ScimUserResource.ScimEmail(
                        "alice@example.com", "work", true)), null);
    }
}
