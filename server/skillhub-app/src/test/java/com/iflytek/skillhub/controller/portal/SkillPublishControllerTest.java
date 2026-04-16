package com.iflytek.skillhub.controller.portal;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import org.mockito.ArgumentMatchers;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.metrics.SkillHubMetrics;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class SkillPublishControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SkillPublishService skillPublishService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @MockBean
    private SkillHubMetrics skillHubMetrics;

    @Test
    void publish_recordsMetricsAfterSuccess() throws Exception {
        SkillVersion version = new SkillVersion(12L, "1.0.0", "usr_1");
        version.setStatus(SkillVersionStatus.PENDING_REVIEW);
        version.setFileCount(1);
        version.setTotalSize(128L);
        ReflectionTestUtils.setField(version, "id", 34L);

        given(skillPublishService.publishFromEntries(
            eq("global"),
            ArgumentMatchers.<List<PackageEntry>>any(),
            eq("usr_1"),
            eq(SkillVisibility.PUBLIC),
            eq(Set.of("SUPER_ADMIN")),
            eq(false)))
            .willReturn(new SkillPublishService.PublishResult(12L, "demo-skill", version));

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
            .andExpect(jsonPath("$.data.slug").value("demo-skill"));

        verify(skillHubMetrics).incrementSkillPublish("global", "PENDING_REVIEW");
    }

    @Test
    void publish_passesWarningConfirmationFlag() throws Exception {
        SkillVersion version = new SkillVersion(12L, "1.0.0", "usr_1");
        version.setStatus(SkillVersionStatus.PENDING_REVIEW);
        version.setFileCount(1);
        version.setTotalSize(128L);
        ReflectionTestUtils.setField(version, "id", 34L);

        given(skillPublishService.publishFromEntries(
            eq("global"),
            ArgumentMatchers.<List<PackageEntry>>any(),
            eq("usr_1"),
            eq(SkillVisibility.PUBLIC),
            eq(Set.of("SUPER_ADMIN")),
            eq(true)))
            .willReturn(new SkillPublishService.PublishResult(12L, "demo-skill", version));

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
