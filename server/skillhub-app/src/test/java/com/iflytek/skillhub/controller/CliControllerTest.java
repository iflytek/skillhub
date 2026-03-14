package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
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

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
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

    @Test
    void whoamiShouldReturnUnauthorizedForAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/v1/whoami"))
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

        mockMvc.perform(get("/api/v1/whoami").with(authentication(auth)))
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

        mockMvc.perform(multipart("/api/v1/check").file(file))
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

        mockMvc.perform(multipart("/api/v1/check").file(file))
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

        mockMvc.perform(multipart("/api/v1/check").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.errors").isNotEmpty());
    }

    @Test
    void checkShouldReturnInvalidForPathTraversalEntry() throws Exception {
        byte[] zipBytes = createZipWithUnsafePath();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "skill.zip",
                "application/zip",
                zipBytes
        );

        mockMvc.perform(multipart("/api/v1/check").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.errors[0]").value(org.hamcrest.Matchers.containsString("escapes package root")))
                .andExpect(jsonPath("$.data.fileCount").value(0))
                .andExpect(jsonPath("$.data.totalSize").value(0));
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

    private byte[] createZipWithUnsafePath() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry unsafeEntry = new ZipEntry("../secrets.txt");
            zos.putNextEntry(unsafeEntry);
            zos.write("hidden".getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
