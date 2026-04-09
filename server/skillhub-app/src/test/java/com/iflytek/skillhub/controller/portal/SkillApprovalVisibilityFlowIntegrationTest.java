package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.SkillhubApplication;
import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.infra.jpa.ReviewTaskJpaRepository;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentJpaRepository;
import com.iflytek.skillhub.search.SearchEmbeddingService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SkillhubApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class SkillApprovalVisibilityFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NamespaceRepository namespaceRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private SkillVersionRepository skillVersionRepository;

    @Autowired
    private ReviewTaskJpaRepository reviewTaskJpaRepository;

    @Autowired
    private SkillSearchDocumentJpaRepository skillSearchDocumentJpaRepository;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @MockBean
    private SearchEmbeddingService searchEmbeddingService;

    @MockBean
    private RbacService rbacService;

    @BeforeEach
    void setUp() {
        when(searchEmbeddingService.embed(anyString())).thenReturn("");
        when(searchEmbeddingService.similarity(anyString(), anyString())).thenReturn(0.0d);
        when(rbacService.getUserRoleCodes("super-1")).thenReturn(Set.of("SUPER_ADMIN"));
    }

    @Test
    void approveReview_indexesGlobalSkillOnlyAfterApproval() throws Exception {
        PendingSkillGraph graph = createPendingGlobalSkill("local-user");

        assertThat(skillSearchDocumentJpaRepository.findBySkillId(graph.skill().getId())).isEmpty();
        assertThat(skillRepository.findById(graph.skill().getId())).get().extracting(Skill::getLatestVersionId).isNull();
        assertThat(skillVersionRepository.findById(graph.version().getId())).get()
                .extracting(SkillVersion::getStatus)
                .isEqualTo(SkillVersionStatus.PENDING_REVIEW);

        mockMvc.perform(post("/api/v1/reviews/" + graph.reviewTask().getId() + "/approve")
                        .contentType("application/json")
                        .content("{\"comment\":\"ship it\"}")
                        .with(authentication(apiAuth("super-1", "SUPER_ADMIN")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(graph.reviewTask().getId()))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewedBy").value("super-1"))
                .andExpect(jsonPath("$.data.reviewComment").value("ship it"));

        Skill savedSkill = skillRepository.findById(graph.skill().getId()).orElseThrow();
        SkillVersion savedVersion = skillVersionRepository.findById(graph.version().getId()).orElseThrow();

        assertThat(savedSkill.getLatestVersionId()).isEqualTo(graph.version().getId());
        assertThat(savedVersion.getStatus()).isEqualTo(SkillVersionStatus.PUBLISHED);
        assertThat(savedVersion.getPublishedAt()).isNotNull();

        SkillSearchDocumentEntity indexedDocument = awaitIndexedDocument(graph.skill().getId());
        assertThat(indexedDocument.getSkillId()).isEqualTo(graph.skill().getId());
        assertThat(indexedDocument.getNamespaceId()).isEqualTo(graph.namespace().getId());
        assertThat(indexedDocument.getNamespaceSlug()).isEqualTo(graph.namespace().getSlug());
        assertThat(indexedDocument.getVisibility()).isEqualTo("PUBLIC");
        assertThat(indexedDocument.getStatus()).isEqualTo("ACTIVE");
        assertThat(indexedDocument.getTitle()).isEqualTo(graph.skill().getDisplayName());
    }

    private PendingSkillGraph createPendingGlobalSkill(String ownerId) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Namespace namespace = new Namespace("global-approval-" + suffix, "Global Approval " + suffix, "system");
        namespace.setType(NamespaceType.GLOBAL);
        namespace = namespaceRepository.save(namespace);

        Skill skill = new Skill(namespace.getId(), "approval-skill-" + suffix, ownerId, SkillVisibility.PUBLIC);
        skill.setDisplayName("Approval Skill " + suffix);
        skill.setSummary("Visible in search only after approval.");
        skill.setCreatedBy(ownerId);
        skill.setUpdatedBy(ownerId);
        skill = skillRepository.save(skill);
        skillRepository.flush();

        SkillVersion version = new SkillVersion(skill.getId(), "1.0.0", ownerId);
        version.setStatus(SkillVersionStatus.PENDING_REVIEW);
        version.setRequestedVisibility(SkillVisibility.PUBLIC);
        version = skillVersionRepository.save(version);
        skillVersionRepository.flush();

        ReviewTask reviewTask = reviewTaskJpaRepository.saveAndFlush(new ReviewTask(version.getId(), namespace.getId(), ownerId));

        return new PendingSkillGraph(namespace, skill, version, reviewTask);
    }

    private SkillSearchDocumentEntity awaitIndexedDocument(Long skillId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        Optional<SkillSearchDocumentEntity> indexed = skillSearchDocumentJpaRepository.findBySkillId(skillId);
        while (indexed.isEmpty() && Instant.now().isBefore(deadline)) {
            Thread.sleep(100L);
            indexed = skillSearchDocumentJpaRepository.findBySkillId(skillId);
        }
        return indexed.orElseThrow(() -> new AssertionError("Expected search document for skill " + skillId));
    }

    private UsernamePasswordAuthenticationToken apiAuth(String userId, String... roles) {
        PlatformPrincipal principal = new PlatformPrincipal(
                userId,
                userId,
                userId + "@example.com",
                "",
                "session",
                Set.of(roles)
        );
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    private record PendingSkillGraph(Namespace namespace, Skill skill, SkillVersion version, ReviewTask reviewTask) {
    }
}
