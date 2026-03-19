package com.iflytek.skillhub.controller.portal;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.review.ReviewService;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillSlugResolutionService;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
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
    private ReviewService reviewService;

    @MockBean
    private SkillPublishService skillPublishService;

    @MockBean
    private SkillSlugResolutionService skillSlugResolutionService;

    @MockBean
    private AuditLogService auditLogService;

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
        given(skillSlugResolutionService.resolve(1L, "demo-skill", "usr_1", SkillSlugResolutionService.Preference.CURRENT_USER))
                .willReturn(skill);
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
        given(skillSlugResolutionService.resolve(1L, "demo-skill", "usr_1", SkillSlugResolutionService.Preference.CURRENT_USER))
                .willReturn(skill);
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
        given(skillSlugResolutionService.resolve(1L, "demo-skill", "usr_1", SkillSlugResolutionService.Preference.CURRENT_USER))
                .willReturn(skill);
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

    @Test
    void withdrawReview_returnsUnifiedEnvelope() throws Exception {
        Namespace namespace = new Namespace("global", "Global", "owner");
        setNamespaceId(namespace, 1L);
        Skill skill = new Skill(1L, "demo-skill", "owner", SkillVisibility.PUBLIC);
        setSkillId(skill, 1L);
        SkillVersion version = new SkillVersion(1L, "1.0.0", "owner");
        setSkillVersionId(version, 2L);
        version.setStatus(SkillVersionStatus.PENDING_REVIEW);

        given(namespaceRepository.findBySlug("global")).willReturn(java.util.Optional.of(namespace));
        given(skillSlugResolutionService.resolve(1L, "demo-skill", "usr_1", SkillSlugResolutionService.Preference.CURRENT_USER))
                .willReturn(skill);
        given(skillVersionRepository.findBySkillIdAndVersion(1L, "1.0.0")).willReturn(java.util.Optional.of(version));
        SkillVersion withdrawn = new SkillVersion(1L, "1.0.0", "owner");
        setSkillVersionId(withdrawn, 2L);
        withdrawn.setStatus(SkillVersionStatus.DRAFT);
        given(reviewService.withdrawReview(2L, "usr_1")).willReturn(withdrawn);

        mockMvc.perform(post("/api/web/skills/global/demo-skill/versions/1.0.0/withdraw-review")
                        .requestAttr("userId", "usr_1")
                        .with(user("usr_1"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.skillId").value(1))
                .andExpect(jsonPath("$.data.versionId").value(2))
                .andExpect(jsonPath("$.data.action").value("WITHDRAW_REVIEW"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void rereleaseVersion_returnsUnifiedEnvelope() throws Exception {
        Namespace namespace = new Namespace("global", "Global", "owner");
        setNamespaceId(namespace, 1L);
        Skill skill = new Skill(1L, "demo-skill", "owner", SkillVisibility.PUBLIC);
        setSkillId(skill, 1L);
        SkillVersion newVersion = new SkillVersion(1L, "1.2.4", "owner");
        setSkillVersionId(newVersion, 3L);
        newVersion.setStatus(SkillVersionStatus.PUBLISHED);

        given(namespaceRepository.findBySlug("global")).willReturn(java.util.Optional.of(namespace));
        given(skillSlugResolutionService.resolve(1L, "demo-skill", "usr_1", SkillSlugResolutionService.Preference.CURRENT_USER))
                .willReturn(skill);
        SkillVersion sourceVersion = new SkillVersion(1L, "1.2.3", "owner");
        setSkillVersionId(sourceVersion, 2L);
        sourceVersion.setStatus(SkillVersionStatus.PUBLISHED);
        given(skillVersionRepository.findBySkillIdAndVersion(1L, "1.2.3")).willReturn(java.util.Optional.of(sourceVersion));
        given(skillPublishService.rereleasePublishedVersion(
                eq(1L),
                eq("1.2.3"),
                eq("1.2.4"),
                eq("usr_1"),
                anyMap()))
                .willReturn(new SkillPublishService.PublishResult(1L, "demo-skill", newVersion));

        mockMvc.perform(post("/api/web/skills/global/demo-skill/versions/1.2.3/rerelease")
                        .requestAttr("userId", "usr_1")
                        .requestAttr("userNsRoles", java.util.Map.of(1L, NamespaceRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetVersion\":\"1.2.4\"}")
                        .with(user("usr_1"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.skillId").value(1))
                .andExpect(jsonPath("$.data.versionId").value(3))
                .andExpect(jsonPath("$.data.action").value("RERELEASE_VERSION"))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    void archiveSkill_acceptsAtPrefixedNamespaceSlug() throws Exception {
        Namespace namespace = new Namespace("global", "Global", "owner");
        setNamespaceId(namespace, 1L);
        Skill skill = new Skill(1L, "demo-skill", "owner", SkillVisibility.PUBLIC);
        setSkillId(skill, 1L);

        given(namespaceRepository.findBySlug("global")).willReturn(java.util.Optional.of(namespace));
        given(skillSlugResolutionService.resolve(1L, "demo-skill", "usr_1", SkillSlugResolutionService.Preference.CURRENT_USER))
                .willReturn(skill);
        given(skillGovernanceService.archiveSkill(eq(1L), eq("usr_1"), anyMap(), nullable(String.class), nullable(String.class), eq("cleanup")))
                .willReturn(skillWithStatus(skill, com.iflytek.skillhub.domain.skill.SkillStatus.ARCHIVED));

        mockMvc.perform(post("/api/web/skills/@global/demo-skill/archive")
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
    void rereleaseVersion_trimsTargetVersionBeforeDelegating() throws Exception {
        Namespace namespace = new Namespace("global", "Global", "owner");
        setNamespaceId(namespace, 1L);
        Skill skill = new Skill(1L, "demo-skill", "owner", SkillVisibility.PUBLIC);
        setSkillId(skill, 1L);
        SkillVersion newVersion = new SkillVersion(1L, "1.2.4", "owner");
        setSkillVersionId(newVersion, 3L);
        newVersion.setStatus(SkillVersionStatus.PUBLISHED);

        given(namespaceRepository.findBySlug("global")).willReturn(java.util.Optional.of(namespace));
        given(skillSlugResolutionService.resolve(1L, "demo-skill", "usr_1", SkillSlugResolutionService.Preference.CURRENT_USER))
                .willReturn(skill);
        SkillVersion sourceVersion = new SkillVersion(1L, "1.2.3", "owner");
        setSkillVersionId(sourceVersion, 2L);
        sourceVersion.setStatus(SkillVersionStatus.PUBLISHED);
        given(skillVersionRepository.findBySkillIdAndVersion(1L, "1.2.3")).willReturn(java.util.Optional.of(sourceVersion));
        given(skillPublishService.rereleasePublishedVersion(
                eq(1L),
                eq("1.2.3"),
                eq("1.2.4"),
                eq("usr_1"),
                anyMap()))
                .willReturn(new SkillPublishService.PublishResult(1L, "demo-skill", newVersion));

        mockMvc.perform(post("/api/web/skills/global/demo-skill/versions/1.2.3/rerelease")
                        .requestAttr("userId", "usr_1")
                        .requestAttr("userNsRoles", java.util.Map.of(1L, NamespaceRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetVersion\":\" 1.2.4  \"}")
                        .with(user("usr_1"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.versionId").value(3))
                .andExpect(jsonPath("$.data.action").value("RERELEASE_VERSION"));

        verify(skillPublishService).rereleasePublishedVersion(
                eq(1L),
                eq("1.2.3"),
                eq("1.2.4"),
                eq("usr_1"),
                anyMap());
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
