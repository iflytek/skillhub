package com.iflytek.skillhub.controller.portal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrganizationControllerTest {

    private static final Instant T0 = Instant.parse("2026-09-22T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @MockBean
    private DeviceAuthService deviceAuthService;

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

    private TestOrganization createOrganization(String slug, String displayName, String memberUserId) {
        persistUser("creator");
        persistUser(memberUserId);
        Organization organization = Organization.create(slug, displayName, "creator", T0);
        entityManager.persist(organization);
        OrganizationMembership membership = OrganizationMembership.provisioned(
                organization.getId(),
                MembershipSourceType.MANUAL,
                "manual-" + memberUserId + "-" + slug,
                memberUserId,
                memberUserId + "@example.com",
                T0
        );
        membership.activate(memberUserId, T0.plusSeconds(1));
        entityManager.persist(membership);
        entityManager.flush();
        return new TestOrganization(organization.getId(), slug);
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
