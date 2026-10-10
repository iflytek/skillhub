package com.iflytek.skillhub.controller.cli;

import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentJpaRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CliRestrictedReadAuthorizationIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ApiTokenService apiTokenService;
    @Autowired UserAccountRepository userAccountRepository;
    @Autowired NamespaceRepository namespaceRepository;
    @Autowired SkillRepository skillRepository;
    @Autowired SkillVersionRepository skillVersionRepository;
    @Autowired SkillSearchDocumentJpaRepository skillSearchDocumentRepository;

    private String namespaceSlug;
    private String skillSlug;
    private String publicSkillSlug;
    private String version;
    private String ownerToken;
    private String outsiderToken;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String ownerId = "private-owner-" + suffix;
        String outsiderId = "private-outsider-" + suffix;
        namespaceSlug = "private-ns-" + suffix;
        skillSlug = Long.toUnsignedString(UUID.randomUUID().getMostSignificantBits());
        publicSkillSlug = "public-skill-" + suffix;
        version = "1.0.0";

        userAccountRepository.save(new UserAccount(
                ownerId, "Private Skill Owner", ownerId + "@example.com", ""));
        userAccountRepository.save(new UserAccount(
                outsiderId, "Private Skill Outsider", outsiderId + "@example.com", ""));
        ownerToken = apiTokenService.createToken(
                ownerId, "owner-token-" + suffix, "[\"skill:read\"]").rawToken();
        outsiderToken = apiTokenService.createToken(
                outsiderId, "outsider-token-" + suffix, "[\"skill:read\"]").rawToken();

        Namespace namespace = namespaceRepository.save(
                new Namespace(namespaceSlug, "Private Namespace", ownerId));
        Skill skill = skillRepository.save(new Skill(
                namespace.getId(), skillSlug, ownerId, SkillVisibility.PRIVATE));
        SkillVersion published = new SkillVersion(skill.getId(), version, ownerId);
        published.setStatus(SkillVersionStatus.PUBLISHED);
        published.setPublishedAt(Instant.parse("2026-07-28T00:00:00Z"));
        published.setDownloadReady(true);
        published = skillVersionRepository.save(published);
        skill.setLatestVersionId(published.getId());
        skillRepository.save(skill);
        skillRepository.flush();
        skillVersionRepository.flush();
        skillSearchDocumentRepository.saveAndFlush(new SkillSearchDocumentEntity(
                skill.getId(),
                namespace.getId(),
                namespaceSlug,
                ownerId,
                skillSlug,
                "Private skill search fixture",
                "private",
                skillSlug,
                "",
                SkillVisibility.PRIVATE.name(),
                skill.getStatus().name()));

        Skill publicSkill = skillRepository.save(new Skill(
                namespace.getId(), publicSkillSlug, ownerId, SkillVisibility.PUBLIC));
        SkillVersion publicPublished = new SkillVersion(publicSkill.getId(), version, ownerId);
        publicPublished.setStatus(SkillVersionStatus.PUBLISHED);
        publicPublished.setPublishedAt(Instant.parse("2026-07-28T00:00:00Z"));
        publicPublished.setDownloadReady(true);
        publicPublished = skillVersionRepository.save(publicPublished);
        publicSkill.setLatestVersionId(publicPublished.getId());
        skillRepository.save(publicSkill);
        skillRepository.flush();
        skillVersionRepository.flush();
        skillSearchDocumentRepository.saveAndFlush(new SkillSearchDocumentEntity(
                publicSkill.getId(),
                namespace.getId(),
                namespaceSlug,
                ownerId,
                skillSlug,
                "Public match for " + publicSkillSlug,
                "public",
                skillSlug,
                "",
                SkillVisibility.PUBLIC.name(),
                publicSkill.getStatus().name()));
    }

    @Test
    void outsiderSearchReturnsMatchingPublicSkillAndOmitsPrivateSkill() throws Exception {
        mockMvc.perform(withBearer(
                        get("/api/cli/v1/skills/search")
                                .param("q", skillSlug)
                                .param("limit", "20"),
                        outsiderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(5)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[*].slug", hasItem(publicSkillSlug)))
                .andExpect(jsonPath("$.data.items[*].slug", not(hasItem(skillSlug))));
    }

    @Test
    void outsiderCannotResolvePrivateSkill() throws Exception {
        assertForbiddenEnvelope(withBearer(
                get("/api/cli/v1/skills/{namespace}/{slug}/resolve", namespaceSlug, skillSlug),
                outsiderToken));
    }

    @Test
    void outsiderCannotDownloadLatestPrivateSkill() throws Exception {
        assertForbiddenEnvelope(withBearer(
                get("/api/cli/v1/skills/{namespace}/{slug}/download", namespaceSlug, skillSlug),
                outsiderToken));
    }

    @Test
    void outsiderCannotDownloadVersionedPrivateSkill() throws Exception {
        assertForbiddenEnvelope(withBearer(
                get("/api/cli/v1/skills/{namespace}/{slug}/versions/{version}/download",
                        namespaceSlug, skillSlug, version),
                outsiderToken));
    }

    private void assertForbiddenEnvelope(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$", aMapWithSize(5)))
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").isString())
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.requestId").isString());
    }

    @Test
    void ownerCanResolvePrivateSkill() throws Exception {
        mockMvc.perform(withBearer(
                        get("/api/cli/v1/skills/{namespace}/{slug}/resolve", namespaceSlug, skillSlug),
                        ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value(skillSlug));
    }

    private MockHttpServletRequestBuilder withBearer(
            MockHttpServletRequestBuilder request,
            String rawToken) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + rawToken);
    }
}
