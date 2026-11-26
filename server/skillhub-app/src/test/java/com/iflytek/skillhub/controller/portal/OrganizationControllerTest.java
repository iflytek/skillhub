package com.iflytek.skillhub.controller.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.audit.AuditLog;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.organization.MembershipSourceType;
import com.iflytek.skillhub.domain.organization.Organization;
import com.iflytek.skillhub.domain.organization.OrganizationMembership;
import com.iflytek.skillhub.domain.organization.OrganizationRole;
import com.iflytek.skillhub.domain.organization.OrganizationRoleBinding;
import com.iflytek.skillhub.domain.user.UserAccount;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrganizationControllerTest {

    private static final Instant T0 = Instant.parse("2026-09-22T00:00:00Z");
    private static final String MEMBERS = "/api/v1/organizations/{organizationId}/members";
    private static final String GRANT = MEMBERS + "/{userId}/roles";
    private static final String REVOKE = "/api/v1/organizations/{organizationId}/role-bindings/{bindingId}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transaction;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @SpyBean
    private AuditLogService auditLogService;

    @Test
    void listOrganizations_returnsOnlyActiveMembershipsForCurrentUser() throws Exception {
        TestOrganization memberOrg = createOrganization("alpha-team", "Alpha Team", "member");
        createRole(memberOrg.id(), "member", OrganizationRole.MEMBER_ADMIN);
        createOrganization("other-team", "Other Team", "other");

        mockMvc.perform(get("/api/v1/organizations")
                        .with(authentication(auth("member"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(memberOrg.id()))
                .andExpect(jsonPath("$.data.items[0].slug").value("alpha-team"))
                .andExpect(jsonPath("$.data.items[0].roles[0]").value("MEMBER_ADMIN"))
                .andExpect(jsonPath("$.data.items[0].permissions.canManageMembers").value(true))
                .andExpect(jsonPath("$.data.items[0].permissions.canManageLoginConnections").value(false));
    }

    @Test
    void listOrganizations_keepsMemberOnlyOrganizationButWithoutManagementEntry() throws Exception {
        createOrganization("member-only", "Member Only", "member");

        mockMvc.perform(get("/api/v1/organizations")
                        .with(authentication(auth("member"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].slug").value("member-only"))
                .andExpect(jsonPath("$.data.items[0].roles").isEmpty())
                .andExpect(jsonPath("$.data.items[0].permissions.canViewManagement").value(false))
                .andExpect(jsonPath("$.data.items[0].permissions.canViewLoginConnections").value(false));
    }

    @Test
    void listOrganizations_rejectsInvalidPagination() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("page", "-1")
                        .with(authentication(auth("member"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void getOrganization_rejectsPlatformAdminWhoIsNotTenantMember() throws Exception {
        TestOrganization organization = createOrganization("tenant-a", "Tenant A", "member");

        mockMvc.perform(get("/api/v1/organizations/{organizationId}", organization.id())
                        .with(authentication(auth("platform-admin", "SUPER_ADMIN"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void listLoginConnections_returnsSanitizedConnectionSummaryForIdentityAdmin() throws Exception {
        TestOrganization organization = createOrganization("identity-team", "Identity Team", "member");
        createRole(organization.id(), "member", OrganizationRole.IDENTITY_ADMIN);
        createLoginConnection(organization.id(), "conn-1", "handle001", "Corporate OIDC");

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/login-connections", organization.id())
                        .with(authentication(auth("member"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value("conn-1"))
                .andExpect(jsonPath("$.data.items[0].publicHandle").value("handle001"))
                .andExpect(jsonPath("$.data.items[0].displayName").value("Corporate OIDC"))
                .andExpect(jsonPath("$.data.items[0].adapterKey").value("oidc"))
                .andExpect(jsonPath("$.data.items[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.data.items[0].typedConfig").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].secretBindingVersion").doesNotExist());
    }

    @Test
    void listLoginConnections_rejectsMemberWithoutConnectionViewRole() throws Exception {
        TestOrganization organization = createOrganization("plain-member", "Plain Member", "member");
        createLoginConnection(organization.id(), "conn-2", "handle002", "Corporate OIDC");

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/login-connections", organization.id())
                        .with(authentication(auth("member"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void ownerAddsMemberAndExplicitlyDelegatesWithoutInheritingSecretPermission() throws Exception {
        TestOrganization organization = createOrganization("owner-team", "Owner Team", "owner");
        createRole(organization.id(), "owner", OrganizationRole.ORG_OWNER);
        persistUser("new-member");

        mockMvc.perform(get("/api/v1/organizations/{organizationId}", organization.id())
                        .with(authentication(auth("owner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions.canManageMembers").value(true))
                .andExpect(jsonPath("$.data.permissions.canManageLoginConnections").value(false))
                .andExpect(jsonPath("$.data.permissions.canRotateLoginSecrets").value(false));

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/members", organization.id())
                        .with(csrf()).with(authentication(auth("owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"new-member\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("new-member"))
                .andExpect(jsonPath("$.data.email").doesNotExist());

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/members/{userId}/roles",
                        organization.id(), "new-member")
                        .with(csrf()).with(authentication(auth("owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEMBER_ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("MEMBER_ADMIN"));

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/members", organization.id())
                        .with(authentication(auth("owner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].email").doesNotExist());
        entityManager.flush();
        List<AuditLog> audits = entityManager.createQuery("""
                        select a from AuditLog a where a.organizationId = :organizationId
                        order by a.createdAt asc
                        """, AuditLog.class)
                .setParameter("organizationId", organization.id())
                .getResultList();
        assertThat(audits).extracting(AuditLog::getAction)
                .contains("ORGANIZATION_MEMBER_ADDED", "ORGANIZATION_ROLE_GRANTED");
        assertThat(audits).allSatisfy(audit -> assertThat(audit.getTargetRef()).isNotBlank());
    }

    @Test
    void memberListProtectsRoleAssignmentsAndHandlesUnlinkedMembershipsAndLargeOffsets() throws Exception {
        TestOrganization organization = createOrganization("list-boundaries", "List Boundaries", "member-list-owner");
        createRole(organization.id(), "member-list-owner", OrganizationRole.ORG_OWNER);
        createMember(organization.id(), "member-list-manager");
        createRole(organization.id(), "member-list-manager", OrganizationRole.MEMBER_ADMIN);
        entityManager.persist(OrganizationMembership.provisioned(
                organization.id(), MembershipSourceType.MANUAL, null,
                "Provisioned member", "private@example.test", T0.plusSeconds(60)));
        entityManager.flush();

        mockMvc.perform(get(MEMBERS, organization.id()).param("size", "1")
                        .with(authentication(auth("member-list-owner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].displayName").value("Provisioned member"))
                .andExpect(jsonPath("$.data.items[0].roles").isEmpty())
                .andExpect(jsonPath("$.data.items[0].email").doesNotExist());
        mockMvc.perform(get(MEMBERS, organization.id())
                        .with(authentication(auth("member-list-manager"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[1].roles").isEmpty())
                .andExpect(jsonPath("$.data.items[2].roles").isEmpty());
        mockMvc.perform(get(MEMBERS, organization.id()).param("page", "2147483647")
                        .param("size", "100").with(authentication(auth("member-list-owner"))))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORGANIZATION_MEMBER_ADDED", "ORGANIZATION_ROLE_GRANTED", "ORGANIZATION_ROLE_REVOKED"})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void auditFailureRollsBackTheWholeMembershipOrRoleWrite(String action) throws Exception {
        boolean adding = action.equals("ORGANIZATION_MEMBER_ADDED");
        boolean revoking = action.equals("ORGANIZATION_ROLE_REVOKED");
        String slug = switch (action) {
            case "ORGANIZATION_MEMBER_ADDED" -> "rollback-member";
            case "ORGANIZATION_ROLE_GRANTED" -> "rollback-grant";
            default -> "rollback-revoke";
        };
        String owner = slug + "-owner";
        String target = slug + "-target";
        String organizationId = transaction.execute(status -> {
            var organization = createOrganization(slug, slug, owner);
            createRole(organization.id(), owner, OrganizationRole.ORG_OWNER);
            if (adding) persistUser(target);
            else createMember(organization.id(), target);
            if (revoking) createRole(organization.id(), target, OrganizationRole.ORG_AUDITOR);
            return organization.id();
        });
        long version = transaction.execute(status -> authorityVersion(organizationId));
        doThrow(new RuntimeException("test audit failure")).when(auditLogService)
                .recordOrganizationAdministration(eq(owner), eq(action), eq(organizationId), any(), any());

        if (adding) {
            mockMvc.perform(post(MEMBERS, organizationId).with(csrf()).with(authentication(auth(owner)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\":\"" + target + "\"}"))
                    .andExpect(status().isInternalServerError());
        } else if (revoking) {
            String bindingId = transaction.execute(status -> roleBindingId(organizationId, target, OrganizationRole.ORG_AUDITOR));
            mockMvc.perform(delete(REVOKE, organizationId, bindingId).with(csrf()).with(authentication(auth(owner))))
                    .andExpect(status().isInternalServerError());
        } else {
            mockMvc.perform(post(GRANT, organizationId, target).with(csrf()).with(authentication(auth(owner)))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ORG_AUDITOR\"}"))
                    .andExpect(status().isInternalServerError());
        }
        verify(auditLogService).recordOrganizationAdministration(eq(owner), eq(action), eq(organizationId), any(), any());
        transaction.executeWithoutResult(status -> {
            assertThat(countMemberships(organizationId)).isEqualTo(adding ? 1 : 2);
            assertThat(countRoleBindings(organizationId, OrganizationRole.ORG_AUDITOR)).isEqualTo(revoking ? 1 : 0);
            assertThat(authorityVersion(organizationId)).isEqualTo(version);
            assertThat(auditActions(organizationId)).isEmpty();
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentMemberAndRoleWritesKeepOneChangeAndProtectTheLastOwner() throws Exception {
        String owner = "concurrent-owner";
        String target = "concurrent-target";
        String organizationId = transaction.execute(status -> {
            var organization = createOrganization("concurrent-role-writes", "Concurrent Role Writes", owner);
            createRole(organization.id(), owner, OrganizationRole.ORG_OWNER);
            persistUser(target);
            return organization.id();
        });
        long version = transaction.execute(status -> authorityVersion(organizationId));
        Callable<Integer> add = () -> mockMvc.perform(post(MEMBERS, organizationId)
                        .with(csrf()).with(authentication(auth(owner))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + target + "\"}"))
                .andReturn().getResponse().getStatus();
        assertThat(concurrently(add, add)).containsExactlyInAnyOrder(200, 409);

        Callable<Integer> grant = () -> mockMvc.perform(post(GRANT, organizationId, target)
                        .with(csrf()).with(authentication(auth(owner))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ORG_OWNER\"}"))
                .andReturn().getResponse().getStatus();
        assertThat(concurrently(grant, grant)).containsExactly(200, 200);
        String ownerBinding = transaction.execute(status -> roleBindingId(organizationId, owner, OrganizationRole.ORG_OWNER));
        String targetBinding = transaction.execute(status -> roleBindingId(organizationId, target, OrganizationRole.ORG_OWNER));
        assertThat(concurrently(
                () -> mockMvc.perform(delete(REVOKE, organizationId, ownerBinding)
                                .with(csrf()).with(authentication(auth(owner)))).andReturn().getResponse().getStatus(),
                () -> mockMvc.perform(delete(REVOKE, organizationId, targetBinding)
                                .with(csrf()).with(authentication(auth(target)))).andReturn().getResponse().getStatus()
        )).containsExactlyInAnyOrder(200, 409);

        transaction.executeWithoutResult(status -> {
            assertThat(countMemberships(organizationId)).isEqualTo(2);
            assertThat(countRoleBindings(organizationId, OrganizationRole.ORG_OWNER)).isEqualTo(1);
            assertThat(authorityVersion(organizationId)).isEqualTo(version + 2);
            assertThat(auditActions(organizationId)).containsExactlyInAnyOrder(
                    "ORGANIZATION_MEMBER_ADDED", "ORGANIZATION_ROLE_GRANTED", "ORGANIZATION_ROLE_REVOKED");
        });
    }

    @Test
    void memberAdminCannotGrantRolesAndPlatformAdminCannotManageTenant() throws Exception {
        TestOrganization organization = createOrganization("restricted-team", "Restricted", "manager");
        createRole(organization.id(), "manager", OrganizationRole.MEMBER_ADMIN);
        persistUser("target");

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/members/{userId}/roles",
                        organization.id(), "manager")
                        .with(csrf()).with(authentication(auth("manager")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"IDENTITY_ADMIN\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/organizations/{organizationId}/members", organization.id())
                        .with(csrf()).with(authentication(auth("outsider", "SUPER_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"target\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void lastOwnerAndCrossOrganizationRoleRevocationAreRejected() throws Exception {
        TestOrganization first = createOrganization("first-team", "First", "owner");
        TestOrganization second = createOrganization("second-team", "Second", "other");
        createRole(first.id(), "owner", OrganizationRole.ORG_OWNER);
        createRole(second.id(), "other", OrganizationRole.ORG_OWNER);
        String bindingId = entityManager.createQuery("""
                        select b.id from OrganizationRoleBinding b
                        where b.organizationId = :organizationId and b.userId = :userId
                        """, String.class)
                .setParameter("organizationId", second.id())
                .setParameter("userId", "other")
                .getSingleResult();
        String firstBindingId = entityManager.createQuery("""
                        select b.id from OrganizationRoleBinding b
                        where b.organizationId = :organizationId and b.userId = :userId
                        """, String.class)
                .setParameter("organizationId", first.id())
                .setParameter("userId", "owner")
                .getSingleResult();

        mockMvc.perform(delete("/api/v1/organizations/{organizationId}/role-bindings/{bindingId}",
                        first.id(), bindingId)
                        .with(csrf()).with(authentication(auth("owner"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/organizations/{organizationId}/role-bindings/{bindingId}",
                        first.id(), firstBindingId)
                        .with(csrf()).with(authentication(auth("owner"))))
                .andExpect(status().isConflict());
    }

    @Test
    void repeatedRoleCommandsDoNotCreateFalseChangeAudits() throws Exception {
        TestOrganization organization = createOrganization("retry-team", "Retry Team", "owner");
        createRole(organization.id(), "owner", OrganizationRole.ORG_OWNER);
        String grantPath = "/api/v1/organizations/{organizationId}/members/{userId}/roles";
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post(grantPath, organization.id(), "owner")
                            .with(csrf()).with(authentication(auth("owner")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"role\":\"IDENTITY_ADMIN\"}"))
                    .andExpect(status().isOk());
        }
        entityManager.flush();
        String bindingId = entityManager.createQuery("""
                        select b.id from OrganizationRoleBinding b
                        where b.organizationId = :organizationId and b.userId = 'owner'
                          and b.role = :role
                        """, String.class)
                .setParameter("organizationId", organization.id())
                .setParameter("role", OrganizationRole.IDENTITY_ADMIN)
                .getSingleResult();
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(delete("/api/v1/organizations/{organizationId}/role-bindings/{bindingId}",
                            organization.id(), bindingId)
                            .with(csrf()).with(authentication(auth("owner"))))
                    .andExpect(status().isOk());
        }
        entityManager.flush();
        List<String> actions = entityManager.createQuery("""
                        select a.action from AuditLog a
                        where a.organizationId = :organizationId
                        """, String.class)
                .setParameter("organizationId", organization.id())
                .getResultList();
        assertThat(actions).containsExactlyInAnyOrder(
                "ORGANIZATION_ROLE_GRANTED", "ORGANIZATION_ROLE_REVOKED");
    }

    private TestOrganization createOrganization(String slug, String displayName, String memberUserId) {
        persistUser("creator");
        Organization organization = Organization.create(slug, displayName, "creator", T0);
        entityManager.persist(organization);
        createMember(organization.getId(), memberUserId);
        return new TestOrganization(organization.getId(), slug);
    }

    private void createMember(String organizationId, String memberUserId) {
        persistUser(memberUserId);
        OrganizationMembership membership = OrganizationMembership.provisioned(
                organizationId,
                MembershipSourceType.MANUAL,
                "manual-" + memberUserId,
                memberUserId,
                memberUserId + "@example.com",
                T0
        );
        membership.activate(memberUserId, T0.plusSeconds(1));
        entityManager.persist(membership);
        entityManager.flush();
    }

    private long authorityVersion(String organizationId) {
        return entityManager.find(Organization.class, organizationId).getAuthorityVersion();
    }

    private long countMemberships(String organizationId) {
        return entityManager.createQuery(
                        "select count(m) from OrganizationMembership m where m.organizationId = :id", Long.class)
                .setParameter("id", organizationId).getSingleResult();
    }

    private long countRoleBindings(String organizationId, OrganizationRole role) {
        return ((Number) entityManager.createNativeQuery("""
                        SELECT count(*) FROM organization_role_binding
                        WHERE organization_id = :id AND role = :role AND status = 'ACTIVE'
                        """)
                .setParameter("id", organizationId).setParameter("role", role.name())
                .getSingleResult()).longValue();
    }

    private String roleBindingId(String organizationId, String userId, OrganizationRole role) {
        return entityManager.createQuery("""
                        select b.id from OrganizationRoleBinding b
                        where b.organizationId = :id and b.userId = :userId and b.role = :role
                        """, String.class)
                .setParameter("id", organizationId).setParameter("userId", userId)
                .setParameter("role", role).getSingleResult();
    }

    private List<String> auditActions(String organizationId) {
        return entityManager.createQuery(
                        "select a.action from AuditLog a where a.organizationId = :id", String.class)
                .setParameter("id", organizationId).getResultList();
    }

    private List<Integer> concurrently(Callable<Integer> first, Callable<Integer> second) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var calls = List.of(first, second).stream().map(call -> executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return call.call();
            })).toList();
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            return List.of(calls.get(0).get(20, TimeUnit.SECONDS), calls.get(1).get(20, TimeUnit.SECONDS));
        }
    }

    private void createRole(String organizationId, String userId, OrganizationRole role) {
        OrganizationRoleBinding binding = OrganizationRoleBinding.grant(
                organizationId,
                userId,
                role,
                "creator",
                T0.plusSeconds(2)
        );
        entityManager.persist(binding);
        entityManager.flush();
    }

    private void createLoginConnection(
            String organizationId,
            String id,
            String publicHandle,
            String displayName
    ) {
        entityManager.createNativeQuery("""
                        INSERT INTO login_connection (
                            id,
                            public_handle,
                            scope_type,
                            organization_id,
                            display_name,
                            status,
                            adapter_key,
                            created_by,
                            created_at,
                            updated_at,
                            version
                        )
                        VALUES (
                            :id,
                            :publicHandle,
                            'ORGANIZATION',
                            :organizationId,
                            :displayName,
                            'DRAFT',
                            'oidc',
                            'creator',
                            :createdAt,
                            :updatedAt,
                            0
                        )
                        """)
                .setParameter("id", id)
                .setParameter("publicHandle", publicHandle)
                .setParameter("organizationId", organizationId)
                .setParameter("displayName", displayName)
                .setParameter("createdAt", T0.plusSeconds(3))
                .setParameter("updatedAt", T0.plusSeconds(3))
                .executeUpdate();
        entityManager.flush();
    }

    private void persistUser(String userId) {
        if (entityManager.find(UserAccount.class, userId) == null) {
            entityManager.persist(new UserAccount(
                    userId,
                    userId,
                    userId + "@example.com",
                    ""
            ));
            entityManager.flush();
        }
    }

    private UsernamePasswordAuthenticationToken auth(String userId, String... roles) {
        Set<String> platformRoles = Set.of(roles);
        PlatformPrincipal principal = new PlatformPrincipal(
                userId,
                userId,
                userId + "@example.com",
                "",
                "github",
                platformRoles
        );
        List<SimpleGrantedAuthority> authorities = platformRoles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    private record TestOrganization(String id, String slug) {
    }
}
