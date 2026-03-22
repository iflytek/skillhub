package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.security.ScannerType;
import com.iflytek.skillhub.domain.security.SecurityAudit;
import com.iflytek.skillhub.domain.security.SecurityAuditRepository;
import com.iflytek.skillhub.domain.security.ScanTaskProducer;
import com.iflytek.skillhub.domain.security.SecurityVerdict;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
    private ScanTaskProducer scanTaskProducer;

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
        audit.setScannedAt(LocalDateTime.of(2026, 3, 20, 16, 0, 0));

        given(securityAuditRepository.findBySkillVersionId(42L)).willReturn(Optional.of(audit));

        mockMvc.perform(get("/api/v1/skills/8/versions/42/security-audit").with(auth("reviewer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(7L))
                .andExpect(jsonPath("$.data.scanId").value("scan-123"))
                .andExpect(jsonPath("$.data.scannerType").value("skill-scanner"))
                .andExpect(jsonPath("$.data.verdict").value("DANGEROUS"))
                .andExpect(jsonPath("$.data.findingsCount").value(1))
                .andExpect(jsonPath("$.data.findings[0].ruleId").value("STATIC-001"));
    }

    @Test
    void getSecurityAudit_returnsNotFoundWhenAuditMissing() throws Exception {
        given(securityAuditRepository.findBySkillVersionId(42L)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/skills/8/versions/42/security-audit").with(auth("reviewer-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void getSecurityAudit_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/skills/8/versions/42/security-audit"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
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
