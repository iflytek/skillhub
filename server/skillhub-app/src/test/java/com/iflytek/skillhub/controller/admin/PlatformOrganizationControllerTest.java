package com.iflytek.skillhub.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.audit.AuditLog;
import com.iflytek.skillhub.domain.organization.Organization;
import com.iflytek.skillhub.domain.organization.OrganizationMembership;
import com.iflytek.skillhub.domain.organization.OrganizationRole;
import com.iflytek.skillhub.domain.organization.OrganizationRoleBinding;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserStatus;
import com.iflytek.skillhub.dto.OrganizationCreateRequest;
import com.iflytek.skillhub.service.PlatformOrganizationCreateAppService;
import jakarta.persistence.EntityManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlatformOrganizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PlatformOrganizationCreateAppService appService;

    @SpyBean
    private AuditLogService auditLogService;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @Test
    void openApiIncludesCreateOrganization() throws Exception {
        String specification = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(specification).contains("\"/api/v1/admin/organizations\"");
        assertThat(specification).contains("\"createOrganization\"");
        String exportPath = System.getProperty("skillhub.openapi.export.path");
        if (exportPath != null) {
            Files.writeString(Path.of(exportPath), specification);
        }
    }

    @Test
    void create_assignsOnlyTheChosenInitialOwnerAndRecordsAudit() throws Exception {
        persistUser("platform-admin");
        persistUser("initial-owner");

        mockMvc.perform(post("/api/v1/admin/organizations")
                        .with(csrf())
                        .with(authentication(auth("platform-admin", "SUPER_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("team-one", "Team One", "initial-owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("team-one"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.authorityVersion").value(1));

        entityManager.flush();
        entityManager.clear();
        Organization organization = entityManager.createQuery(
                        "select o from Organization o where o.slug = :slug", Organization.class)
                .setParameter("slug", "team-one")
                .getSingleResult();
        List<OrganizationMembership> memberships = entityManager.createQuery(
                        "select m from OrganizationMembership m where m.organizationId = :id",
                        OrganizationMembership.class)
                .setParameter("id", organization.getId())
                .getResultList();
        assertThat(memberships).singleElement()
                .satisfies(member -> {
                    assertThat(member.getUserId()).isEqualTo("initial-owner");
                    assertThat(member.getStatus().name()).isEqualTo("ACTIVE");
                });
        List<OrganizationRoleBinding> roles = entityManager.createQuery(
                        "select b from OrganizationRoleBinding b where b.organizationId = :id",
                        OrganizationRoleBinding.class)
                .setParameter("id", organization.getId())
                .getResultList();
        assertThat(roles).singleElement()
                .satisfies(binding -> {
                    assertThat(binding.getUserId()).isEqualTo("initial-owner");
                    assertThat(binding.getRole()).isEqualTo(OrganizationRole.ORG_OWNER);
                });
        List<AuditLog> audits = entityManager.createQuery(
                        "select a from AuditLog a where a.organizationId = :id", AuditLog.class)
                .setParameter("id", organization.getId())
                .getResultList();
        assertThat(audits).singleElement()
                .satisfies(audit -> {
                    assertThat(audit.getActorUserId()).isEqualTo("platform-admin");
                    assertThat(audit.getAction()).isEqualTo("ORGANIZATION_CREATED");
                    assertThat(audit.getTargetRef()).isEqualTo(organization.getId());
                    assertThat(audit.getResult()).isEqualTo("SUCCESS");
                });

        mockMvc.perform(get("/api/v1/organizations/{id}", organization.getId())
                        .with(authentication(auth("platform-admin", "SUPER_ADMIN"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/organizations/{id}", organization.getId())
                        .with(authentication(auth("initial-owner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("ORG_OWNER"));
    }

    @Test
    void create_rejectsNonAdminBeforeWriting() throws Exception {
        persistUser("initial-owner");
        mockMvc.perform(post("/api/v1/admin/organizations")
                        .with(csrf())
                        .with(authentication(auth("member")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("no-access", "No Access", "initial-owner")))
                .andExpect(status().isForbidden());
        assertThat(organizationCount()).isZero();
    }

    @Test
    void create_rejectsIneligibleOwnerAndDuplicateSlug() throws Exception {
        persistUser("platform-admin");
        persistUser("initial-owner");
        UserAccount disabled = persistUser("disabled-owner");
        disabled.setStatus(UserStatus.DISABLED);
        UserAccount merged = persistUser("merged-owner");
        merged.setMergedToUserId("initial-owner");
        entityManager.persist(UserAccount.systemAccount(
                "system-owner", "System", null, ""));
        entityManager.flush();

        mockMvc.perform(post("/api/v1/admin/organizations")
                        .with(csrf())
                        .with(authentication(auth("platform-admin", "SUPER_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("disabled-team", "Disabled Team", "disabled-owner")))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/admin/organizations")
                        .with(csrf())
                        .with(authentication(auth("platform-admin", "SUPER_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("missing-team", "Missing Team", "missing-owner")))
                .andExpect(status().isNotFound());
        for (String ineligible : List.of("merged-owner", "system-owner")) {
            mockMvc.perform(post("/api/v1/admin/organizations")
                            .with(csrf())
                            .with(authentication(auth("platform-admin", "SUPER_ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request(ineligible, "Ineligible Team", ineligible)))
                    .andExpect(status().isConflict());
        }
        assertThat(organizationCount()).isZero();

        mockMvc.perform(post("/api/v1/admin/organizations")
                        .with(csrf())
                        .with(authentication(auth("platform-admin", "SUPER_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("team-one", "Team One", "initial-owner")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/organizations")
                        .with(csrf())
                        .with(authentication(auth("platform-admin", "SUPER_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("team-one", "Other Name", "initial-owner")))
                .andExpect(status().isConflict());
        assertThat(organizationCount()).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void create_rollsBackAllWritesWhenAuditFails() {
        transactionTemplate.executeWithoutResult(status -> persistUser("rollback-owner"));
        long organizationsBefore = count("Organization");
        long membershipsBefore = count("OrganizationMembership");
        long rolesBefore = count("OrganizationRoleBinding");
        long auditsBefore = count("AuditLog");

        doThrow(new IllegalStateException("forced audit failure"))
                .when(auditLogService).recordOrganizationCreated(
                        eq("platform-admin"), anyString(), any());

        assertThatThrownBy(() -> appService.create(
                new OrganizationCreateRequest("rollback-team", "Rollback Team", "rollback-owner"),
                "platform-admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("forced audit failure");

        assertThat(count("Organization")).isEqualTo(organizationsBefore);
        assertThat(count("OrganizationMembership")).isEqualTo(membershipsBefore);
        assertThat(count("OrganizationRoleBinding")).isEqualTo(rolesBefore);
        assertThat(count("AuditLog")).isEqualTo(auditsBefore);
    }

    private UserAccount persistUser(String userId) {
        UserAccount user = new UserAccount(userId, userId, userId + "@example.com", "");
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private long organizationCount() {
        entityManager.flush();
        return entityManager.createQuery("select count(o) from Organization o", Long.class)
                .getSingleResult();
    }

    private long count(String entityName) {
        return entityManager.createQuery("select count(e) from " + entityName + " e", Long.class)
                .getSingleResult();
    }

    private String request(String slug, String displayName, String owner) {
        return "{\"slug\":\"" + slug + "\",\"displayName\":\"" + displayName
                + "\",\"initialOwnerUserId\":\"" + owner + "\"}";
    }

    private UsernamePasswordAuthenticationToken auth(String userId, String... roles) {
        Set<String> platformRoles = Set.of(roles);
        PlatformPrincipal principal = new PlatformPrincipal(
                userId, userId, userId + "@example.com", "", "github", platformRoles);
        List<SimpleGrantedAuthority> authorities = platformRoles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }
}
