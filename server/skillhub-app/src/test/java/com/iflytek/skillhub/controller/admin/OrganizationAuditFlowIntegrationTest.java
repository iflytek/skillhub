package com.iflytek.skillhub.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.SkillhubApplication;
import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.audit.AuditLog;
import com.iflytek.skillhub.domain.audit.AuditLogRepository;
import com.iflytek.skillhub.domain.organization.OrganizationRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.infra.jpa.AuditLogJpaRepository;
import com.iflytek.skillhub.infra.jpa.UserAccountJpaRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = SkillhubApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class OrganizationAuditFlowIntegrationTest {

    private static final String ADMIN_ID = "organization-audit-admin";
    private static final String OWNER_ID = "organization-audit-owner";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserAccountJpaRepository userAccountRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private AuditLogRepository auditLogPort;
    @SpyBean private AuditLogJpaRepository auditLogRepository;
    @MockBean private DeviceAuthService deviceAuthService;

    @Test
    void createOrganizationPersistsTenantCorrelatedAuditWithRequestId() throws Exception {
        saveUsers();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "audit-success-" + suffix;
        String requestId = "organization-create-" + suffix;

        mockMvc.perform(post("/api/v1/admin/organizations")
                        .header("X-Request-Id", requestId)
                        .with(authentication(superAdminAuth()))
                        .with(csrf())
                        .contentType("application/json")
                        .content(createRequest(slug)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.data.slug").value(slug));

        String organizationId = organizationRepository.findBySlug(slug).orElseThrow().getId();
        AuditLog event = auditLogRepository.findAll().stream()
                .filter(log -> "ORGANIZATION_CREATED".equals(log.getAction()))
                .filter(log -> organizationId.equals(log.getOrganizationId()))
                .findFirst()
                .orElseThrow();

        assertThat(event.getActorUserId()).isEqualTo(ADMIN_ID);
        assertThat(event.getTargetType()).isEqualTo("ORGANIZATION");
        assertThat(event.getTargetReference()).isEqualTo(organizationId);
        assertThat(event.getResult()).isEqualTo("SUCCESS");
        assertThat(event.getRequestId()).isEqualTo(requestId);
        assertThat(event.getCreatedAt()).isNotNull();
        assertThat(event.getDetailJson())
                .contains("ACTIVE")
                .doesNotContainIgnoringCase(
                        "token",
                        "secret",
                        "password",
                        "credential",
                        "assertion"
                );
    }

    @Test
    void createOrganizationRollsBackWhenAuditPersistenceFails() throws Exception {
        saveUsers();
        String slug = "audit-rollback-" + UUID.randomUUID().toString().substring(0, 8);
        doThrow(new DataIntegrityViolationException("forced audit failure"))
                .when(auditLogPort).save(any(AuditLog.class));

        mockMvc.perform(post("/api/v1/admin/organizations")
                        .header("X-Request-Id", "organization-create-rollback")
                        .with(authentication(superAdminAuth()))
                        .with(csrf())
                        .contentType("application/json")
                        .content(createRequest(slug)))
                .andExpect(status().isInternalServerError());

        assertThat(organizationRepository.findBySlug(slug)).isEmpty();
    }

    private void saveUsers() {
        saveUserIfAbsent(ADMIN_ID, "Organization Audit Admin");
        saveUserIfAbsent(OWNER_ID, "Organization Audit Owner");
    }

    private void saveUserIfAbsent(String userId, String displayName) {
        if (!userAccountRepository.existsById(userId)) {
            userAccountRepository.saveAndFlush(new UserAccount(
                    userId,
                    displayName,
                    userId + "@example.test",
                    null
            ));
        }
    }

    private String createRequest(String slug) {
        return """
                {
                  "slug": "%s",
                  "displayName": "Audit Organization",
                  "initialOwnerUserId": "%s"
                }
                """.formatted(slug, OWNER_ID);
    }

    private UsernamePasswordAuthenticationToken superAdminAuth() {
        PlatformPrincipal principal = new PlatformPrincipal(
                ADMIN_ID,
                "Organization Audit Admin",
                ADMIN_ID + "@example.test",
                "",
                "session",
                Set.of("SUPER_ADMIN")
        );
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );
    }
}
