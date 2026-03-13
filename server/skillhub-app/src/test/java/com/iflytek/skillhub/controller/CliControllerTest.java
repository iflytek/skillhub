package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.metrics.SkillHubMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CliControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;
    @MockBean
    private SkillPublishService skillPublishService;
    @MockBean
    private SkillHubMetrics skillHubMetrics;
    @MockBean
    private AuditLogService auditLogService;

    @Test
    void whoamiShouldReturnUnauthorizedForAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/v1/cli/whoami"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void whoamiShouldReturnCurrentPrincipal() throws Exception {
        given(namespaceMemberRepository.findByUserId("user-7")).willReturn(List.of());

        PlatformPrincipal principal = new PlatformPrincipal(
            "user-7",
            "cli-user",
            "cli@example.com",
            "",
            "api_token",
            Set.of("SKILL_ADMIN")
        );

        var auth = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_SKILL_ADMIN"))
        );

        mockMvc.perform(get("/api/v1/cli/whoami").with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.msg").isNotEmpty())
            .andExpect(jsonPath("$.data.userId").value("user-7"))
            .andExpect(jsonPath("$.data.displayName").value("cli-user"))
            .andExpect(jsonPath("$.data.authType").value("api_token"))
            .andExpect(jsonPath("$.data.platformRoles[0]").value("SKILL_ADMIN"))
            .andExpect(jsonPath("$.timestamp").isNotEmpty())
            .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void checkShouldReturnValidForValidPackage() throws Exception {
        byte[] zipBytes = createValidSkillZip();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "skill.zip",
                "application/zip",
                zipBytes
        );

        mockMvc.perform(multipart("/api/v1/cli/check").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.errors").isEmpty())
                .andExpect(jsonPath("$.data.fileCount").value(2))
                .andExpect(jsonPath("$.data.totalSize").isNumber());
    }

    @Test
    void checkShouldReturnInvalidForMissingSkillMd() throws Exception {
        byte[] zipBytes = createInvalidSkillZip();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "skill.zip",
                "application/zip",
                zipBytes
        );

        mockMvc.perform(multipart("/api/v1/cli/check").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.errors").isNotEmpty())
                .andExpect(jsonPath("$.data.errors[0]").value("Missing required file: SKILL.md at root"));
    }

    @Test
    void checkShouldReturnInvalidForDisallowedExtension() throws Exception {
        byte[] zipBytes = createZipWithDisallowedFile();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "skill.zip",
                "application/zip",
                zipBytes
        );

        mockMvc.perform(multipart("/api/v1/cli/check").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.errors").isNotEmpty());
    }

    @Test
    void publishShouldPassPlatformRolesToDomainService() throws Exception {
        SkillVersion version = new SkillVersion(12L, "1.0.0", "user-7");
        version.setStatus(SkillVersionStatus.PUBLISHED);
        version.setFileCount(1);
        version.setTotalSize(128L);

        given(skillPublishService.publishFromEntries(
                eq("global"),
                anyList(),
                eq("user-7"),
                eq(SkillVisibility.PUBLIC),
                eq(Set.of("SUPER_ADMIN"))))
                .willReturn(new SkillPublishService.PublishResult(12L, "demo-skill", version));

        PlatformPrincipal principal = new PlatformPrincipal(
                "user-7",
                "cli-user",
                "cli@example.com",
                "",
                "api_token",
                Set.of("SUPER_ADMIN")
        );

        var auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "skill.zip",
                "application/zip",
                createValidSkillZip()
        );

        mockMvc.perform(multipart("/api/v1/cli/publish")
                        .file(file)
                        .param("namespace", "global")
                        .param("visibility", "PUBLIC")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    private byte[] createValidSkillZip() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            String skillMdContent = """
                ---
                name: test-skill
                description: A test skill
                version: 1.0.0
                ---
                # Test Skill
                This is a test skill.
                """;
            ZipEntry skillMdEntry = new ZipEntry("SKILL.md");
            zos.putNextEntry(skillMdEntry);
            zos.write(skillMdContent.getBytes());
            zos.closeEntry();

            ZipEntry readmeEntry = new ZipEntry("README.md");
            zos.putNextEntry(readmeEntry);
            zos.write("# README\nThis is a readme.".getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] createInvalidSkillZip() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry readmeEntry = new ZipEntry("README.md");
            zos.putNextEntry(readmeEntry);
            zos.write("# README".getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] createZipWithDisallowedFile() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            String skillMdContent = """
                ---
                name: test-skill
                description: A test skill
                version: 1.0.0
                ---
                # Test Skill
                """;
            ZipEntry skillMdEntry = new ZipEntry("SKILL.md");
            zos.putNextEntry(skillMdEntry);
            zos.write(skillMdContent.getBytes());
            zos.closeEntry();

            ZipEntry exeEntry = new ZipEntry("malware.exe");
            zos.putNextEntry(exeEntry);
            zos.write("bad content".getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
