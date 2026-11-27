package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.domain.label.LabelDefinition;
import com.iflytek.skillhub.domain.label.LabelDefinitionRepository;
import com.iflytek.skillhub.domain.label.LabelType;
import com.iflytek.skillhub.domain.label.SkillLabel;
import com.iflytek.skillhub.domain.label.SkillLabelRepository;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillAvatarCandidate;
import com.iflytek.skillhub.infra.jpa.SkillAvatarCandidateJpaRepository;
import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.SkillFileRepository;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVersionStatsRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.repository.GithubSkillSourceQueryRepository;
import com.iflytek.skillhub.service.SkillSuiteAppService;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DshBearerCompatibilityIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ApiTokenService apiTokenService;
    @Autowired UserAccountRepository userAccountRepository;
    @Autowired NamespaceRepository namespaceRepository;
    @Autowired NamespaceMemberRepository namespaceMemberRepository;
    @Autowired SkillRepository skillRepository;
    @Autowired LabelDefinitionRepository labelDefinitions;
    @Autowired SkillLabelRepository skillLabels;
    @Autowired SkillAvatarCandidateJpaRepository avatars;
    @Autowired SkillVersionRepository versionRepository;
    @Autowired SkillFileRepository fileRepository;
    @Autowired ObjectStorageService storage;
    @MockBean SkillSuiteAppService skillSuites;
    // The optional GitHub attribution projection uses PostgreSQL-specific SQL.
    @MockBean GithubSkillSourceQueryRepository githubSources;
    @MockBean SkillVersionStatsRepository versionStats;

    @Test
    void publicLabelsRemainReadableWithBearerAndAnonymously() throws Exception {
        String token = token(createUser(), "[\"skill:submit\"]");
        for (String path : new String[] {"/api/v1/labels", "/api/web/labels"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
            mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    @Test
    void signedInReaderWithSameSlugDraftCanReadThePublishedPackageAndOwnDraftStatus() throws Exception {
        String publisher = createUser();
        String reader = createUser();
        String namespace = "dsh-" + UUID.randomUUID();
        Namespace space = namespaceRepository.save(new Namespace(namespace, "DSH", publisher));
        Skill draft = skillRepository.save(new Skill(space.getId(), "demo", reader, SkillVisibility.PRIVATE));
        SkillVersion draftVersion = versionRepository.save(new SkillVersion(draft.getId(), "2.0.0", reader));
        SkillVersion conflictingDraft = new SkillVersion(draft.getId(), "1.0.0", reader);
        conflictingDraft.setStatus(SkillVersionStatus.PENDING_REVIEW);
        conflictingDraft = versionRepository.save(conflictingDraft);
        Skill published = skillRepository.save(new Skill(space.getId(), "demo", publisher, SkillVisibility.PUBLIC));
        SkillVersion version = new SkillVersion(published.getId(), "1.0.0", publisher);
        version.setStatus(SkillVersionStatus.PUBLISHED);
        version.setPublishedAt(Instant.now());
        version.setDownloadReady(true);
        version = versionRepository.save(version);
        published.setLatestVersionId(version.getId());
        String avatarKey = "test/" + namespace + "/avatar.png";
        SkillAvatarCandidate avatar = new SkillAvatarCandidate(published.getId(), publisher, avatarKey,
                "b".repeat(64), SkillVisibility.PUBLIC, false, false, null);
        avatar.activate();
        avatar = avatars.save(avatar);
        published.activateAvatar(avatar.getId(), SkillVisibility.PUBLIC);
        skillRepository.save(published);
        var label = labelDefinitions.save(new LabelDefinition(namespace, LabelType.RECOMMENDED, false, 0, publisher));
        skillLabels.save(new SkillLabel(published.getId(), label.getId(), publisher));
        skillRepository.flush();
        SkillVersion older = new SkillVersion(published.getId(), "0.9.0", publisher);
        older.setStatus(SkillVersionStatus.PUBLISHED);
        versionRepository.save(older);
        when(skillSuites.findVisibleMemberships(eq(published.getId()), eq(reader), anyMap(), anySet(), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(), 0, 0, 20));

        String key = "test/" + namespace + "/SKILL.md";
        byte[] bytes = "# Published demo".getBytes(StandardCharsets.UTF_8);
        storage.putObject(key, new ByteArrayInputStream(bytes), bytes.length, "text/plain");
        try {
            byte[] image = {1, 2, 3};
            storage.putObject(avatarKey, new ByteArrayInputStream(image), image.length, "image/png");
            fileRepository.save(new SkillFile(version.getId(), "SKILL.md", (long) bytes.length,
                    "text/plain", "a".repeat(64), key));
            String token = token(reader, "[]");
            String skill = "/api/v1/skills/" + namespace + "/demo";
            String cli = "/api/cli/v1/skills/" + namespace + "/demo";
            mockMvc.perform(get(cli + "/resolve")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.versionId").value(version.getId()))
                    .andExpect(jsonPath("$.data.fingerprint").isNotEmpty());
            mockMvc.perform(get(cli + "/resolve").header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.versionId").value(version.getId()))
                    .andExpect(jsonPath("$.data.fingerprint").isNotEmpty());
            mockMvc.perform(get("/api/v1/resolve/" + namespace + "--demo").param("version", "1.0.0")
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.match.version").value("1.0.0"));
            mockMvc.perform(get(skill).header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(published.getId()));
            mockMvc.perform(get(skill + "/versions/1.0.0/file").param("path", "SKILL.md")
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk()).andExpect(content().bytes(bytes));
            mockMvc.perform(get(skill + "/versions/2.0.0").header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(draftVersion.getId()));
            mockMvc.perform(get(skill + "/avatar").header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk()).andExpect(content().bytes(image));
            mockMvc.perform(get(skill + "/labels").header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].slug").value(namespace));
            mockMvc.perform(get(skill + "/tags").header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].versionId").value(version.getId()));
            mockMvc.perform(get(skill + "/versions/compare").param("from", "0.9.0").param("to", "1.0.0")
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.to").value("1.0.0"));
            mockMvc.perform(get(skill + "/versions/1.0.0").header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(version.getId()));
            String exact = "/api/v1/me/skills/versions/" + conflictingDraft.getId();
            mockMvc.perform(get(exact).header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(exact).header(HttpHeaders.AUTHORIZATION, token(reader, "[\"skill:read\"]")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(conflictingDraft.getId()))
                    .andExpect(jsonPath("$.data.version").value("1.0.0"))
                    .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));
            mockMvc.perform(get(exact).header(HttpHeaders.AUTHORIZATION, token(publisher, "[\"skill:read\"]")))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get(exact)).andExpect(status().isUnauthorized());
            String bundleKey = "packages/" + published.getId() + "/" + version.getId() + "/bundle.zip";
            byte[] bundle;
            try (var out = new java.io.ByteArrayOutputStream();
                 var zip = new java.util.zip.ZipOutputStream(out)) {
                zip.putNextEntry(new java.util.zip.ZipEntry("SKILL.md"));
                zip.write(bytes);
                zip.closeEntry();
                zip.finish();
                bundle = out.toByteArray();
            }
            storage.putObject(bundleKey, new ByteArrayInputStream(bundle), bundle.length, "application/zip");
            try {
                mockMvc.perform(get(cli + "/versions/1.0.0/download")).andExpect(status().isOk())
                        .andExpect(content().bytes(bundle));
                mockMvc.perform(get(cli + "/versions/1.0.0/download").header(HttpHeaders.AUTHORIZATION, token))
                        .andExpect(status().isOk()).andExpect(content().bytes(bundle));
            } finally {
                storage.deleteObject(bundleKey);
            }
        } finally {
            storage.deleteObject(key);
            storage.deleteObject(avatarKey);
        }
    }

    @Test
    void rosterRequiresBothTokenPermissionAndTeamMembership() throws Exception {
        String owner = createUser();
        String slug = "dsh-" + UUID.randomUUID().toString().substring(0, 8);
        Namespace team = new Namespace(slug, "DSH Team", owner);
        team.setType(NamespaceType.TEAM);
        team = namespaceRepository.save(team);
        namespaceMemberRepository.save(new NamespaceMember(team.getId(), owner, NamespaceRole.OWNER));
        String restricted = token(owner, "[\"skill:read\"]");
        String member = token(owner, "[\"namespace:manage\"]");
        String outsider = token(createUser(), "[\"namespace:read\"]");
        String path = "/api/v1/namespaces/" + slug + "/members";

        mockMvc.perform(get("/api/cli/v1/auth/whoami").header(HttpHeaders.AUTHORIZATION, restricted))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me/namespaces").header(HttpHeaders.AUTHORIZATION, restricted))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].slug").value(slug));
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, restricted))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer error=\"insufficient_scope\", scope=\"namespace:read\""));
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].userId").value(owner));
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, outsider))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    private String createUser() {
        String userId = "dsh-" + UUID.randomUUID();
        userAccountRepository.save(new UserAccount(userId, "DSH User", null, ""));
        return userId;
    }

    private String token(String userId, String scopes) {
        return "Bearer " + apiTokenService.createToken(userId, UUID.randomUUID().toString(), scopes).rawToken();
    }
}
