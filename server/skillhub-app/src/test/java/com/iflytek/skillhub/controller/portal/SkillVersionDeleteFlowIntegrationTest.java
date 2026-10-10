package com.iflytek.skillhub.controller.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class SkillVersionDeleteFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NamespaceRepository namespaceRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private SkillVersionRepository skillVersionRepository;

    @Autowired
    private ReviewTaskRepository reviewTaskRepository;

    @MockBean
    private ObjectStorageService objectStorageService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @Test
    void deleteRejectedVersion_removesOnlyItsReviewHistory() throws Exception {
        String ownerId = "owner-1";
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Namespace namespace = namespaceRepository.save(
                new Namespace("version-delete-" + suffix, "Version Delete " + suffix, ownerId)
        );

        Skill skill = new Skill(namespace.getId(), "demo-skill-" + suffix, ownerId, SkillVisibility.PUBLIC);
        skill.setCreatedBy(ownerId);
        skill.setUpdatedBy(ownerId);
        skill = skillRepository.save(skill);

        SkillVersion rejectedVersion = new SkillVersion(skill.getId(), "1.0.0", ownerId);
        rejectedVersion.setStatus(SkillVersionStatus.REJECTED);
        rejectedVersion = skillVersionRepository.save(rejectedVersion);

        SkillVersion retainedVersion = new SkillVersion(skill.getId(), "2.0.0", ownerId);
        retainedVersion.setStatus(SkillVersionStatus.REJECTED);
        retainedVersion = skillVersionRepository.save(retainedVersion);

        ReviewTask rejectedTask = new ReviewTask(
                rejectedVersion.getId(), skill.getId(), namespace.getId(), rejectedVersion.getVersion(), ownerId);
        rejectedTask.setStatus(ReviewTaskStatus.REJECTED);
        rejectedTask = reviewTaskRepository.save(rejectedTask);

        ReviewTask approvedTask = new ReviewTask(
                rejectedVersion.getId(), skill.getId(), namespace.getId(), rejectedVersion.getVersion(), ownerId);
        approvedTask.setStatus(ReviewTaskStatus.APPROVED);
        approvedTask = reviewTaskRepository.save(approvedTask);

        ReviewTask retainedTask = new ReviewTask(
                retainedVersion.getId(), skill.getId(), namespace.getId(), retainedVersion.getVersion(), ownerId);
        retainedTask.setStatus(ReviewTaskStatus.REJECTED);
        retainedTask = reviewTaskRepository.save(retainedTask);

        Long skillId = skill.getId();
        Long rejectedVersionId = rejectedVersion.getId();

        mockMvc.perform(delete("/api/web/skills/{namespace}/{slug}/versions/{version}",
                        namespace.getSlug(), skill.getSlug(), rejectedVersion.getVersion())
                        .with(authentication(portalAuth(ownerId, "USER")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.skillId").value(skillId))
                .andExpect(jsonPath("$.data.versionId").value(rejectedVersionId))
                .andExpect(jsonPath("$.data.action").value("DELETE_VERSION"))
                .andExpect(jsonPath("$.data.status").value("1.0.0"));

        assertThat(skillVersionRepository.findById(rejectedVersion.getId())).isEmpty();
        assertThat(skillVersionRepository.findById(retainedVersion.getId())).isPresent();
        assertThat(reviewTaskRepository.findById(rejectedTask.getId())).isEmpty();
        assertThat(reviewTaskRepository.findById(approvedTask.getId())).isEmpty();
        assertThat(reviewTaskRepository.findById(retainedTask.getId())).isPresent();
        verify(objectStorageService).deleteObjects(argThat(keys ->
                keys.equals(List.of("packages/" + skillId + "/" + rejectedVersionId + "/bundle.zip"))
        ));
    }

    private UsernamePasswordAuthenticationToken portalAuth(String userId, String... roles) {
        PlatformPrincipal principal = new PlatformPrincipal(
                userId,
                userId,
                userId + "@example.com",
                "",
                "session",
                Set.of(roles)
        );
        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }
}
