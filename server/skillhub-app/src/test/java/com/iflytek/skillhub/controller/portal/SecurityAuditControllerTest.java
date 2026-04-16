package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.security.ScannerType;
import com.iflytek.skillhub.domain.security.SecurityAudit;
import com.iflytek.skillhub.domain.security.SecurityAuditRepository;
import com.iflytek.skillhub.domain.security.ScanTaskProducer;
import com.iflytek.skillhub.domain.security.SecurityVerdict;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SecurityAuditRepository securityAuditRepository;

    @MockBean
    private SkillRepository skillRepository;

    @MockBean
    private SkillVersionRepository skillVersionRepository;

    @MockBean
    private ScanTaskProducer scanTaskProducer;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @Test
    void getSecurityAudit_returnsAuditPayload() throws Exception {
        SecurityAudit audit = new SecurityAudit(42L, ScannerType.SKILL_SCANNER);
        setField(audit, "id", 7L);
        audit.setScanId("scan-123");
        audit.setVerdict(SecurityVerdict.DANGEROUS);
        audit.setIsSafe(false);
        audit.setMaxSeverity("HIGH");
        audit.setFindingsCount(1);
        audit.setFindings("""
                [{"ruleId":"STATIC-001","severity":"HIGH","category":"code-execution","title":"Dynamic execution","message":"avoid eval","filePath":"src/main.py","lineNumber":12,"codeSnippet":"eval(user_input)"}]
                """.trim());
        audit.setScanDurationSeconds(1.25);
        audit.setScannedAt(Instant.parse("2026-03-20T08:00:00Z"));
        given(skillVersionRepository.findById(42L)).willReturn(java.util.Optional.of(skillVersion(42L, 8L)));
        given(skillRepository.findById(8L)).willReturn(java.util.Optional.of(skill(8L, "reviewer-1")));

        given(securityAuditRepository.findLatestActiveByVersionId(42L)).willReturn(List.of(audit));

        mockMvc.perform(get("/api/v1/skills/8/versions/42/security-audit")
                        .with(auth("reviewer-1"))
                        .requestAttr("userNsRoles", Map.of(5L, NamespaceRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(7L))
                .andExpect(jsonPath("$.data[0].scanId").value("scan-123"))
                .andExpect(jsonPath("$.data[0].scannerType").value("skill-scanner"))
                .andExpect(jsonPath("$.data[0].verdict").value("DANGEROUS"))
                .andExpect(jsonPath("$.data[0].findingsCount").value(1))
                .andExpect(jsonPath("$.data[0].findings[0].ruleId").value("STATIC-001"));
    }

    @Test
    void getSecurityAudit_returnsEmptyListWhenAuditMissing() throws Exception {
        given(skillVersionRepository.findById(42L)).willReturn(java.util.Optional.of(skillVersion(42L, 8L)));
        given(skillRepository.findById(8L)).willReturn(java.util.Optional.of(skill(8L, "reviewer-1")));
        given(securityAuditRepository.findLatestActiveByVersionId(42L)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/skills/8/versions/42/security-audit")
                        .with(auth("reviewer-1"))
                        .requestAttr("userNsRoles", Map.of(5L, NamespaceRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void getSecurityAudit_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/skills/8/versions/42/security-audit"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void getSecurityAudit_rejectsVersionSkillMismatch() throws Exception {
        given(skillVersionRepository.findById(42L)).willReturn(java.util.Optional.of(skillVersion(42L, 9L)));

        mockMvc.perform(get("/api/v1/skills/8/versions/42/security-audit").with(auth("reviewer-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void getSecurityAudit_forbidsUnauthorizedViewer() throws Exception {
        given(skillVersionRepository.findById(42L)).willReturn(java.util.Optional.of(skillVersion(42L, 8L)));
        given(skillRepository.findById(8L)).willReturn(java.util.Optional.of(skill(8L, "owner-1")));

        mockMvc.perform(get("/api/v1/skills/8/versions/42/security-audit").with(auth("viewer-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void getSecurityAudit_allowsNamespaceAdminForPendingUnpublishedSkill() throws Exception {
        SecurityAudit audit = new SecurityAudit(42L, ScannerType.SKILL_SCANNER);
        setField(audit, "id", 9L);
        audit.setScanId("scan-team-admin");
        audit.setVerdict(SecurityVerdict.SAFE);
        audit.setIsSafe(true);
        audit.setMaxSeverity("LOW");
        audit.setFindingsCount(0);
        given(skillVersionRepository.findById(42L)).willReturn(java.util.Optional.of(skillVersion(42L, 8L)));
        given(skillRepository.findById(8L)).willReturn(java.util.Optional.of(skill(8L, "owner-1")));
        given(securityAuditRepository.findLatestActiveByVersionId(42L)).willReturn(List.of(audit));
        given(namespaceMemberRepository.findByUserId("team-admin"))
                .willReturn(List.of(new NamespaceMember(5L, "team-admin", NamespaceRole.ADMIN)));

        mockMvc.perform(get("/api/v1/skills/8/versions/42/security-audit")
                        .with(auth("team-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(9L))
                .andExpect(jsonPath("$.data[0].scanId").value("scan-team-admin"));
    }

    private RequestPostProcessor auth(String userId) {
        PlatformPrincipal principal = new PlatformPrincipal(
                userId,
                "reviewer",
                "reviewer@example.com",
                "",
                "local",
                Set.of()
        );
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        return authentication(authenticationToken);
    }

    private SkillVersion skillVersion(Long versionId, Long skillId) {
        SkillVersion version = new SkillVersion(skillId, "1.0.0", "owner-1");
        setField(version, "id", versionId);
        return version;
    }

    private Skill skill(Long skillId, String ownerId) {
        Skill skill = new Skill(5L, "caldav-calendar", ownerId, SkillVisibility.PRIVATE);
        setField(skill, "id", skillId);
        setField(skill, "status", SkillStatus.ACTIVE);
        return skill;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
