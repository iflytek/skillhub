package com.iflytek.skillhub.controller.portal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.collection.SkillCollection;
import com.iflytek.skillhub.domain.collection.SkillCollectionContributorService;
import com.iflytek.skillhub.domain.collection.SkillCollectionDomainService;
import com.iflytek.skillhub.domain.collection.SkillCollectionMembershipService;
import com.iflytek.skillhub.domain.collection.SkillReadableForActorPort;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class SkillCollectionSecurityIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SkillCollectionDomainService skillCollectionDomainService;

    @Autowired
    private SkillCollectionContributorService skillCollectionContributorService;

    @Autowired
    private SkillCollectionMembershipService skillCollectionMembershipService;

    @Autowired
    private NamespaceRepository namespaceRepository;

    @Autowired
    private SkillRepository skillRepository;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @MockBean
    private SkillReadableForActorPort skillReadableForActorPort;

    @Test
    void contributorCannotUpdateCollectionMetadata() throws Exception {
        TestFixture fixture = createFixture();

        MvcResult result = mockMvc.perform(patch("/api/web/collections/{id}", fixture.collection().getId())
                        .with(authentication(portalAuth(fixture.contributorUserId(), "USER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Contributor update",
                                "description", "should fail",
                                "slug", fixture.collection().getSlug()
                        ))))
                .andReturn();

        assertContributorDenied(result);
    }

    @Test
    void contributorCannotDeleteCollection() throws Exception {
        TestFixture fixture = createFixture();

        MvcResult result = mockMvc.perform(delete("/api/web/collections/{id}", fixture.collection().getId())
                        .with(authentication(portalAuth(fixture.contributorUserId(), "USER")))
                        .with(csrf()))
                .andReturn();

        assertContributorDenied(result);
    }

    @Test
    void contributorCannotManageContributors() throws Exception {
        TestFixture fixture = createFixture();

        MvcResult result = mockMvc.perform(post("/api/web/collections/{id}/contributors", fixture.collection().getId())
                        .with(authentication(portalAuth(fixture.contributorUserId(), "USER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", "user-c"))))
                .andReturn();

        assertContributorDenied(result);
    }

    @Test
    void contributorCannotChangeCollectionVisibility() throws Exception {
        TestFixture fixture = createFixture();

        MvcResult result = mockMvc.perform(patch("/api/web/collections/{id}/visibility", fixture.collection().getId())
                        .with(authentication(portalAuth(fixture.contributorUserId(), "USER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("visibility", "PRIVATE"))))
                .andReturn();

        assertContributorDenied(result);
    }

    @Test
    void ownerCannotMutateCollectionWithoutCsrfToken() throws Exception {
        TestFixture fixture = createFixture();

        mockMvc.perform(patch("/api/web/collections/{id}", fixture.collection().getId())
                        .with(authentication(portalAuth(fixture.ownerUserId(), "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Missing CSRF",
                                "description", "should be rejected",
                                "slug", fixture.collection().getSlug()
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void contributorCanAddSkillToCollection() throws Exception {
        TestFixture fixture = createFixture();

        mockMvc.perform(post("/api/web/collections/{id}/skills", fixture.collection().getId())
                        .with(authentication(portalAuth(fixture.contributorUserId(), "USER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("skillId", fixture.publicSkill().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.skillId").value(fixture.publicSkill().getId()));
    }

    @Test
    void ownerCanUpdateCollectionMetadata() throws Exception {
        TestFixture fixture = createFixture();

        mockMvc.perform(patch("/api/web/collections/{id}", fixture.collection().getId())
                        .with(authentication(portalAuth(fixture.ownerUserId(), "USER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Owner update",
                                "description", "allowed",
                                "slug", fixture.collection().getSlug()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("Owner update"));
    }

    @Test
    void ownerCanUpdateCollectionMetadataViaV1Route() throws Exception {
        TestFixture fixture = createFixture();

        mockMvc.perform(patch("/api/v1/collections/{id}", fixture.collection().getId())
                        .with(authentication(portalAuth(fixture.ownerUserId(), "USER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Owner update v1",
                                "description", "allowed",
                                "slug", fixture.collection().getSlug()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("Owner update v1"));
    }

    @Test
    void strangerCannotReadPrivateCollection() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String ownerUserId = "owner-" + suffix;
        SkillCollection privateCollection = skillCollectionDomainService.createCollection(
                ownerUserId,
                "Private " + suffix,
                "private fixture",
                SkillVisibility.PRIVATE,
                "private-" + suffix,
                false,
                ownerUserId
        );

        MvcResult result = mockMvc.perform(get("/api/web/collections/{id}", privateCollection.getId())
                        .with(authentication(portalAuth("stranger-" + suffix, "USER"))))
                .andReturn();

        assertNotFoundLike(result, "error.skillCollection.notFound");
    }

    @Test
    void adminCanUpdateForeignCollectionMetadata() throws Exception {
        TestFixture fixture = createFixture();

        mockMvc.perform(patch("/api/web/collections/{id}", fixture.collection().getId())
                        .with(authentication(portalAuth("admin-" + UUID.randomUUID(), "SKILL_ADMIN")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Admin update",
                                "description", "admin override",
                                "slug", fixture.collection().getSlug()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("Admin update"));
    }

    @Test
    void addUnreadableSkillIsRejectedInt01() throws Exception {
        TestFixture fixture = createFixture();

        MvcResult result = mockMvc.perform(post("/api/web/collections/{id}/skills", fixture.collection().getId())
                        .with(authentication(portalAuth(fixture.contributorUserId(), "USER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("skillId", fixture.privateForeignSkill().getId()))))
                .andReturn();

        assertDomainBadRequest(result, "cannot read this skill");
    }

    @Test
    void addDuplicateSkillIsRejectedInt02() throws Exception {
        TestFixture fixture = createFixture();

        mockMvc.perform(post("/api/web/collections/{id}/skills", fixture.collection().getId())
                        .with(authentication(portalAuth(fixture.contributorUserId(), "USER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("skillId", fixture.publicSkill().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MvcResult duplicate = mockMvc.perform(post("/api/web/collections/{id}/skills", fixture.collection().getId())
                        .with(authentication(portalAuth(fixture.contributorUserId(), "USER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("skillId", fixture.publicSkill().getId()))))
                .andReturn();

        assertDomainBadRequest(duplicate, "already in the collection");
    }

    @Test
    void anonymousPublicEndpointFiltersInvisibleMembersVis03() throws Exception {
        TestFixture fixture = createFixture();
        skillCollectionMembershipService.addSkill(
                fixture.collection().getId(),
                fixture.ownerUserId(),
                fixture.publicSkill().getId(),
                false
        );
        skillCollectionMembershipService.addSkill(
                fixture.collection().getId(),
                fixture.ownerUserId(),
                fixture.ownerPrivateSkill().getId(),
                false
        );

        mockMvc.perform(get("/api/web/public/collections/{ownerId}/{slug}",
                        fixture.ownerUserId(),
                        fixture.collection().getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.members.length()").value(1))
                .andExpect(jsonPath("$.data.members[0].skillId").value(fixture.publicSkill().getId()));
    }

    @Test
    void anonymousV1PublicEndpointFiltersInvisibleMembersVis03() throws Exception {
        TestFixture fixture = createFixture();
        addPublicAndOwnerPrivateMembers(fixture);

        mockMvc.perform(get("/api/v1/public/collections/{ownerId}/{slug}",
                        fixture.ownerUserId(),
                        fixture.collection().getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.members.length()").value(1))
                .andExpect(jsonPath("$.data.members[0].skillId").value(fixture.publicSkill().getId()));
    }

    @Test
    void contributorAuthenticatedReadIncludesHiddenMemberCount() throws Exception {
        TestFixture fixture = createFixture();
        addPublicAndOwnerPrivateMembers(fixture);

        mockMvc.perform(get("/api/web/collections/{id}", fixture.collection().getId())
                        .with(authentication(portalAuth(fixture.contributorUserId(), "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.members.length()").value(1))
                .andExpect(jsonPath("$.data.members[0].skillId").value(fixture.publicSkill().getId()))
                .andExpect(jsonPath("$.data.additionalMembersHiddenFromActorCount").value(1));
    }

    @Test
    void ownerAuthenticatedReadKeepsHiddenCountAtZero() throws Exception {
        TestFixture fixture = createFixture();
        addPublicAndOwnerPrivateMembers(fixture);

        mockMvc.perform(get("/api/web/collections/{id}", fixture.collection().getId())
                        .with(authentication(portalAuth(fixture.ownerUserId(), "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.members.length()").value(2))
                .andExpect(jsonPath("$.data.additionalMembersHiddenFromActorCount").value(0));
    }

    @Test
    void publicEndpointKeepsHiddenCountAtZero() throws Exception {
        TestFixture fixture = createFixture();
        addPublicAndOwnerPrivateMembers(fixture);

        mockMvc.perform(get("/api/web/public/collections/{ownerId}/{slug}",
                        fixture.ownerUserId(),
                        fixture.collection().getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.members.length()").value(1))
                .andExpect(jsonPath("$.data.additionalMembersHiddenFromActorCount").value(0));
    }

    @Test
    void anonymousCannotReadPrivateCollectionViaPublicEndpoint() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String ownerUserId = "owner-" + suffix;
        SkillCollection privateCollection = skillCollectionDomainService.createCollection(
                ownerUserId,
                "Private " + suffix,
                "private fixture",
                SkillVisibility.PRIVATE,
                "private-public-route-" + suffix,
                false,
                ownerUserId
        );

        MvcResult result = mockMvc.perform(get("/api/web/public/collections/{ownerId}/{slug}",
                        ownerUserId,
                        privateCollection.getSlug()))
                .andReturn();

        assertNotFoundLike(result, "error.skillCollection.notFound");
    }

    private void assertContributorDenied(MvcResult result) throws Exception {
        int status = result.getResponse().getStatus();
        String body = result.getResponse().getContentAsString();
        boolean deniedByStatus = status == 403;
        boolean deniedByCode = status == 400 && body.contains("Contributors cannot perform");
        assertTrue(deniedByStatus || deniedByCode,
                () -> "Expected contributor denied status/body but got status=" + status + " body=" + body);
    }

    private void addPublicAndOwnerPrivateMembers(TestFixture fixture) {
        skillCollectionMembershipService.addSkill(
                fixture.collection().getId(),
                fixture.ownerUserId(),
                fixture.publicSkill().getId(),
                false
        );
        skillCollectionMembershipService.addSkill(
                fixture.collection().getId(),
                fixture.ownerUserId(),
                fixture.ownerPrivateSkill().getId(),
                false
        );
    }

    private void assertDomainBadRequest(MvcResult result, String expectedCode) throws Exception {
        int status = result.getResponse().getStatus();
        String body = result.getResponse().getContentAsString();
        boolean matches = status == 400 && body.contains(expectedCode);
        assertTrue(matches, () -> "Expected status=400 and code=" + expectedCode + " but got status="
                + status + " body=" + body);
    }

    private void assertNotFoundLike(MvcResult result, String expectedCode) throws Exception {
        int status = result.getResponse().getStatus();
        String body = result.getResponse().getContentAsString();
        boolean matches = (status == 400 || status == 404)
                && (body.contains(expectedCode) || body.contains("Skill collection not found"));
        assertTrue(matches, () -> "Expected not-found-like response with code=" + expectedCode + " but got status="
                + status + " body=" + body);
    }

    private TestFixture createFixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String ownerUserId = "owner-" + suffix;
        String contributorUserId = "contributor-" + suffix;

        SkillCollection collection = skillCollectionDomainService.createCollection(
                ownerUserId,
                "Collection " + suffix,
                "security test fixture",
                SkillVisibility.PUBLIC,
                "security-" + suffix,
                false,
                ownerUserId
        );
        skillCollectionContributorService.addContributor(collection.getId(), ownerUserId, contributorUserId, false);

        Namespace namespace = namespaceRepository.save(
                new Namespace("global-collection-" + suffix, "Global Collection " + suffix, ownerUserId)
        );
        Skill publicSkill = new Skill(namespace.getId(), "collection-skill-" + suffix, ownerUserId, SkillVisibility.PUBLIC);
        publicSkill.setDisplayName("Collection Skill " + suffix);
        publicSkill = skillRepository.save(publicSkill);

        Skill ownerPrivateSkill = new Skill(namespace.getId(), "private-owner-skill-" + suffix, ownerUserId, SkillVisibility.PRIVATE);
        ownerPrivateSkill.setDisplayName("Owner Private Skill " + suffix);
        ownerPrivateSkill = skillRepository.save(ownerPrivateSkill);

        String outsiderUserId = "outsider-" + suffix;
        Namespace outsiderNamespace = namespaceRepository.save(
                new Namespace("global-outsider-" + suffix, "Global Outsider " + suffix, outsiderUserId)
        );
        Skill privateForeignSkill = new Skill(
                outsiderNamespace.getId(),
                "private-foreign-skill-" + suffix,
                outsiderUserId,
                SkillVisibility.PRIVATE
        );
        privateForeignSkill.setDisplayName("Private Foreign Skill " + suffix);
        privateForeignSkill = skillRepository.save(privateForeignSkill);

        when(skillReadableForActorPort.canActingUserReadSkill(eq(ownerUserId), eq(publicSkill.getId()))).thenReturn(true);
        when(skillReadableForActorPort.canActingUserReadSkill(eq(contributorUserId), eq(publicSkill.getId()))).thenReturn(true);
        when(skillReadableForActorPort.canActingUserReadSkill(nullable(String.class), eq(publicSkill.getId()))).thenReturn(true);

        when(skillReadableForActorPort.canActingUserReadSkill(eq(ownerUserId), eq(ownerPrivateSkill.getId())))
                .thenReturn(true);
        when(skillReadableForActorPort.canActingUserReadSkill(isNull(), eq(ownerPrivateSkill.getId())))
                .thenReturn(false);

        when(skillReadableForActorPort.canActingUserReadSkill(eq(contributorUserId), eq(privateForeignSkill.getId())))
                .thenReturn(false);
        when(skillReadableForActorPort.canActingUserReadSkill(eq(ownerUserId), eq(privateForeignSkill.getId())))
                .thenReturn(false);

        return new TestFixture(ownerUserId, contributorUserId, collection, publicSkill, ownerPrivateSkill, privateForeignSkill);
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
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    private record TestFixture(
            String ownerUserId,
            String contributorUserId,
            SkillCollection collection,
            Skill publicSkill,
            Skill ownerPrivateSkill,
            Skill privateForeignSkill
    ) {
    }
}
