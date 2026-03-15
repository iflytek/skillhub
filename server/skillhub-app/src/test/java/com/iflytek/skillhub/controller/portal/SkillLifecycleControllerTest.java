package com.iflytek.skillhub.controller.portal;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillGovernanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class SkillLifecycleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NamespaceRepository namespaceRepository;

    @MockBean
    private SkillRepository skillRepository;

    @MockBean
    private SkillVersionRepository skillVersionRepository;

    @MockBean
    private SkillGovernanceService skillGovernanceService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @Test
    void archiveSkill_returnsUnifiedEnvelope() throws Exception {
        Namespace namespace = new Namespace("global", "Global", "owner");
        setNamespaceId(namespace, 1L);
        Skill skill = new Skill(1L, "demo-skill", "owner", SkillVisibility.PUBLIC);
        setSkillId(skill, 1L);

        given(namespaceRepository.findBySlug("global")).willReturn(java.util.Optional.of(namespace));
        given(skillRepository.findByNamespaceIdAndSlug(1L, "demo-skill")).willReturn(java.util.Optional.of(skill));
        given(skillGovernanceService.archiveSkill(eq(1L), eq("usr_1"), anyMap(), nullable(String.class), nullable(String.class), eq("cleanup")))
                .willReturn(skillWithStatus(skill, com.iflytek.skillhub.domain.skill.SkillStatus.ARCHIVED));

        mockMvc.perform(post("/api/web/skills/global/demo-skill/archive")
                        .requestAttr("userId", "usr_1")
                        .requestAttr("userNsRoles", java.util.Map.of(1L, NamespaceRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"cleanup\"}")
                        .with(user("usr_1"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.skillId").value(1))
                .andExpect(jsonPath("$.data.action").value("ARCHIVE"))
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
    }

    @Test
    void unarchiveSkill_returnsUnifiedEnvelope() throws Exception {
        Namespace namespace = new Namespace("global", "Global", "owner");
        setNamespaceId(namespace, 1L);
        Skill skill = new Skill(1L, "demo-skill", "owner", SkillVisibility.PUBLIC);
        setSkillId(skill, 1L);
        skill.setStatus(com.iflytek.skillhub.domain.skill.SkillStatus.ARCHIVED);

        given(namespaceRepository.findBySlug("global")).willReturn(java.util.Optional.of(namespace));
        given(skillRepository.findByNamespaceIdAndSlug(1L, "demo-skill")).willReturn(java.util.Optional.of(skill));
        given(skillGovernanceService.unarchiveSkill(eq(1L), eq("usr_1"), anyMap(), nullable(String.class), nullable(String.class)))
                .willReturn(skillWithStatus(skill, com.iflytek.skillhub.domain.skill.SkillStatus.ACTIVE));

        mockMvc.perform(post("/api/web/skills/global/demo-skill/unarchive")
                        .requestAttr("userId", "usr_1")
                        .requestAttr("userNsRoles", java.util.Map.of(1L, NamespaceRole.ADMIN))
                        .with(user("usr_1"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.skillId").value(1))
                .andExpect(jsonPath("$.data.action").value("UNARCHIVE"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void deleteVersion_returnsUnifiedEnvelope() throws Exception {
        Namespace namespace = new Namespace("global", "Global", "owner");
        setNamespaceId(namespace, 1L);
        Skill skill = new Skill(1L, "demo-skill", "owner", SkillVisibility.PUBLIC);
        setSkillId(skill, 1L);
        SkillVersion version = new SkillVersion(2L, "1.0.0", "owner");
        setSkillVersionId(version, 2L);
        version.setStatus(SkillVersionStatus.DRAFT);

        given(namespaceRepository.findBySlug("global")).willReturn(java.util.Optional.of(namespace));
        given(skillRepository.findByNamespaceIdAndSlug(1L, "demo-skill")).willReturn(java.util.Optional.of(skill));
        given(skillVersionRepository.findBySkillIdAndVersion(1L, "1.0.0")).willReturn(java.util.Optional.of(version));

        mockMvc.perform(delete("/api/web/skills/global/demo-skill/versions/1.0.0")
                        .requestAttr("userId", "usr_1")
                        .requestAttr("userNsRoles", java.util.Map.of(1L, NamespaceRole.ADMIN))
                        .with(user("usr_1"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.skillId").value(1))
                .andExpect(jsonPath("$.data.versionId").value(2))
                .andExpect(jsonPath("$.data.action").value("DELETE_VERSION"))
                .andExpect(jsonPath("$.data.status").value("1.0.0"));
    }

    private Skill skillWithStatus(Skill skill, com.iflytek.skillhub.domain.skill.SkillStatus status) {
        skill.setStatus(status);
        return skill;
    }

    private void setNamespaceId(Namespace namespace, Long id) {
        org.springframework.test.util.ReflectionTestUtils.setField(namespace, "id", id);
    }

    private void setSkillId(Skill skill, Long id) {
        org.springframework.test.util.ReflectionTestUtils.setField(skill, "id", id);
    }

    private void setSkillVersionId(SkillVersion version, Long id) {
        org.springframework.test.util.ReflectionTestUtils.setField(version, "id", id);
    }
}
