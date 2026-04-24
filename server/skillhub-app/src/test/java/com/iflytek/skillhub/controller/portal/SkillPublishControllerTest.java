package com.iflytek.skillhub.controller.portal;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.dto.PublishResponse;
import com.iflytek.skillhub.dto.PublishResultDetailResponse;
import com.iflytek.skillhub.service.SkillPublishAppService;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class SkillPublishControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SkillPublishAppService skillPublishAppService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @Test
    void publish_recordsMetricsAfterSuccess() throws Exception {
        given(skillPublishAppService.publish(
            eq("global"),
            org.mockito.ArgumentMatchers.any(MultipartFile.class),
            eq(SkillVisibility.PUBLIC),
            eq("usr_1"),
            eq(Set.of("SUPER_ADMIN")),
            eq(false)))
            .willReturn(publishResponse("demo-skill", "PENDING_REVIEW"));

        PlatformPrincipal principal = new PlatformPrincipal(
            "usr_1",
            "publisher",
            "publisher@example.com",
            "",
            "local",
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
            buildZipBytes()
        );

        mockMvc.perform(multipart("/api/v1/skills/global/publish")
                .file(file)
                .param("visibility", "PUBLIC")
                .with(authentication(auth))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.skillId").value(12))
            .andExpect(jsonPath("$.data.slug").value("demo-skill"))
            .andExpect(jsonPath("$.data.results[0].slug").value("demo-skill"));
    }

    @Test
    void publish_passesWarningConfirmationFlag() throws Exception {
        given(skillPublishAppService.publish(
            eq("global"),
            org.mockito.ArgumentMatchers.any(MultipartFile.class),
            eq(SkillVisibility.PUBLIC),
            eq("usr_1"),
            eq(Set.of("SUPER_ADMIN")),
            eq(true)))
            .willReturn(publishResponse("demo-skill", "PENDING_REVIEW"));

        PlatformPrincipal principal = new PlatformPrincipal(
            "usr_1",
            "publisher",
            "publisher@example.com",
            "",
            "local",
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
            buildZipBytes()
        );

        mockMvc.perform(multipart("/api/v1/skills/global/publish")
                .file(file)
                .param("visibility", "PUBLIC")
                .param("confirmWarnings", "true")
                .with(authentication(auth))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        verify(skillPublishAppService).publish(
                eq("global"),
                org.mockito.ArgumentMatchers.any(MultipartFile.class),
                eq(SkillVisibility.PUBLIC),
                eq("usr_1"),
                eq(Set.of("SUPER_ADMIN")),
                eq(true));
    }

    private PublishResponse publishResponse(String slug, String status) {
        PublishResultDetailResponse detail = new PublishResultDetailResponse(
                "",
                12L,
                "global",
                slug,
                "1.0.0",
                status,
                1,
                128L
        );
        return new PublishResponse(12L, "global", slug, "1.0.0", status, 1, 128L, List.of(detail));
    }

    private byte[] buildZipBytes() throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("SKILL.md"));
            zip.write("""
                ---
                name: Demo Skill
                version: 1.0.0
                ---
                """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return output.toByteArray();
        }
    }
}
