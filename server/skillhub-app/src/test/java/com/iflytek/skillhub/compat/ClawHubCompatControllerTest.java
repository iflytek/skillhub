package com.iflytek.skillhub.compat;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.entity.ApiToken;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.SkillLifecycleVersionResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.service.SkillSearchAppService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClawHubCompatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @MockBean
    private SkillSearchAppService skillSearchAppService;

    @MockBean
    private SkillQueryService skillQueryService;

    @MockBean
    private ApiTokenService apiTokenService;

    @MockBean
    private UserAccountRepository userAccountRepository;

    @MockBean
    private UserRoleBindingRepository userRoleBindingRepository;

    @MockBean
    private CompatSkillLookupService compatSkillLookupService;

    @MockBean
    private SkillPublishService skillPublishService;

    @MockBean
    private AuditLogService auditLogService;

    @Test
    void search_returns_mapped_results() throws Exception {
        when(skillSearchAppService.search("test", null, "relevance", 0, 20, null, null))
                .thenReturn(new SkillSearchAppService.SearchResponse(
                        List.of(new SkillSummaryResponse(
                                1L,
                                "my-skill",
                                "My Skill",
                                "test summary",
                                "PUBLIC",
                                "ACTIVE",
                                10L,
                                5,
                                BigDecimal.valueOf(4.5),
                                2,
                                "global",
                                Instant.parse("2026-03-13T09:00:00Z"),
                                false,
                                new SkillLifecycleVersionResponse(11L, "1.2.0", "PUBLISHED"),
                                new SkillLifecycleVersionResponse(11L, "1.2.0", "PUBLISHED"),
                                null,
                                "PUBLISHED")),
                        1,
                        0,
                        20
                ));

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results[0].slug").value("my-skill"))
                .andExpect(jsonPath("$.results[0].summary").value("test summary"))
                .andExpect(jsonPath("$.results[0].version").value("1.2.0"));
    }

    @Test
    void search_withBearerToken_shouldProjectNamespaceRolesIntoRequestContext() throws Exception {
        ApiToken token = new ApiToken("user-7", "cli", "sk_test", "hash", "[]");
        UserAccount user = new UserAccount("user-7", "Alice", "alice@example.com", null);
        var nsRoles = java.util.Map.of(9L, NamespaceRole.MEMBER);

        when(apiTokenService.validateToken("raw-token")).thenReturn(Optional.of(token));
        when(userAccountRepository.findById("user-7")).thenReturn(Optional.of(user));
        when(userRoleBindingRepository.findByUserId("user-7")).thenReturn(List.of());
        when(namespaceMemberRepository.findByUserId("user-7"))
                .thenReturn(List.of(new NamespaceMember(9L, "user-7", NamespaceRole.MEMBER)));
        when(skillSearchAppService.search("token-search", null, "relevance", 0, 20, "user-7", nsRoles))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(), 0, 0, 20));

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "token-search")
                        .header("Authorization", "Bearer raw-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray());

        verify(skillSearchAppService).search("token-search", null, "relevance", 0, 20, "user-7", nsRoles);
        verify(apiTokenService).touchLastUsed(same(token));
    }

    @Test
    void downloadQuery_withBearerToken_shouldProjectNamespaceRolesIntoRequestContext() throws Exception {
        ApiToken token = new ApiToken("user-7", "cli", "sk_test", "hash", "[]");
        UserAccount user = new UserAccount("user-7", "Alice", "alice@example.com", null);
        var nsRoles = java.util.Map.of(9L, NamespaceRole.MEMBER);

        when(apiTokenService.validateToken("raw-token")).thenReturn(Optional.of(token));
        when(userAccountRepository.findById("user-7")).thenReturn(Optional.of(user));
        when(userRoleBindingRepository.findByUserId("user-7")).thenReturn(List.of());
        when(namespaceMemberRepository.findByUserId("user-7"))
                .thenReturn(List.of(new NamespaceMember(9L, "user-7", NamespaceRole.MEMBER)));
        when(compatSkillLookupService.findByLegacySlug("private-skill"))
                .thenReturn(legacyCompatContext("team-ai", "private-skill"));
        when(compatSkillLookupService.canAccess(any(), eq("user-7"), eq(nsRoles))).thenReturn(true);

        mockMvc.perform(get("/api/v1/download")
                        .param("slug", "private-skill")
                        .param("version", "latest")
                        .header("Authorization", "Bearer raw-token"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/api/v1/skills/team-ai/private-skill/download"));

        verify(compatSkillLookupService).canAccess(any(), eq("user-7"), eq(nsRoles));
        verify(apiTokenService).touchLastUsed(same(token));
    }

    @Test
    void resolve_returns_correct_downloadUrl() throws Exception {
        when(skillQueryService.resolveVersion("global", "my-skill", null, "latest", null, null, java.util.Map.of()))
                .thenReturn(new SkillQueryService.ResolvedVersionDTO(
                        1L, "global", "my-skill", "latest", 2L, "sha", true, "/api/v1/skills/global/my-skill/download"));
        mockMvc.perform(get("/api/v1/resolve/my-skill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.match.version").value("latest"))
                .andExpect(jsonPath("$.latestVersion.version").value("latest"));
    }

    @Test
    void resolve_with_namespace_returns_correct_downloadUrl() throws Exception {
        when(skillQueryService.resolveVersion("team-ai", "my-skill", null, "latest", null, null, java.util.Map.of()))
                .thenReturn(new SkillQueryService.ResolvedVersionDTO(
                        1L, "team-ai", "my-skill", "latest", 2L, "sha", true, "/api/v1/skills/team-ai/my-skill/download"));
        mockMvc.perform(get("/api/v1/resolve/team-ai--my-skill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.match.version").value("latest"))
                .andExpect(jsonPath("$.latestVersion.version").value("latest"));
    }

    @Test
    void resolve_query_with_canonical_slug_returns_correct_downloadUrl() throws Exception {
        when(skillQueryService.resolveVersion("team-ai", "my-skill", null, "latest", null, null, java.util.Map.of()))
                .thenReturn(new SkillQueryService.ResolvedVersionDTO(
                        1L, "team-ai", "my-skill", "latest", 2L, "sha", true, "/api/v1/skills/team-ai/my-skill/download"));

        mockMvc.perform(get("/api/v1/resolve")
                        .param("slug", "team-ai--my-skill")
                        .param("version", "latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.match.version").value("latest"))
                .andExpect(jsonPath("$.latestVersion.version").value("latest"));

        verify(skillQueryService).resolveVersion("team-ai", "my-skill", null, "latest", null, null, java.util.Map.of());
    }

    @Test
    void resolve_query_with_legacy_slug_keeps_legacy_lookup_behavior() throws Exception {
        when(compatSkillLookupService.findByLegacySlug("my-skill"))
                .thenReturn(legacyCompatContext("global", "my-skill"));
        when(compatSkillLookupService.canAccess(any(), isNull(), anyMap())).thenReturn(true);
        when(skillQueryService.resolveVersion("global", "my-skill", null, "latest", null, null, java.util.Map.of()))
                .thenReturn(new SkillQueryService.ResolvedVersionDTO(
                        1L, "global", "my-skill", "latest", 2L, "sha", true, "/api/v1/skills/global/my-skill/download"));

        mockMvc.perform(get("/api/v1/resolve")
                        .param("slug", "my-skill")
                        .param("version", "latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.match.version").value("latest"))
                .andExpect(jsonPath("$.latestVersion.version").value("latest"));

        verify(compatSkillLookupService).canAccess(any(), isNull(), anyMap());
        verify(skillQueryService).resolveVersion("global", "my-skill", null, "latest", null, null, java.util.Map.of());
    }

    @Test
    void download_query_with_canonical_slug_redirects_to_namespace_skill_download() throws Exception {
        mockMvc.perform(get("/api/v1/download")
                        .param("slug", "team-ai--my-skill")
                        .param("version", "latest"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/api/v1/skills/team-ai/my-skill/download"));
    }

    @Test
    void download_query_with_legacy_slug_keeps_legacy_lookup_behavior() throws Exception {
        when(compatSkillLookupService.findByLegacySlug("my-skill"))
                .thenReturn(legacyCompatContext("global", "my-skill"));
        when(compatSkillLookupService.canAccess(any(), isNull(), anyMap())).thenReturn(true);
        mockMvc.perform(get("/api/v1/download")
                        .param("slug", "my-skill")
                        .param("version", "latest"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/api/v1/skills/global/my-skill/download"));

        verify(compatSkillLookupService).canAccess(any(), isNull(), anyMap());
    }

    @Test
    void resolve_with_version_returns_specified_version() throws Exception {
        when(skillQueryService.resolveVersion("global", "my-skill", "1.0.0", null, null, null, java.util.Map.of()))
                .thenReturn(new SkillQueryService.ResolvedVersionDTO(
                        1L, "global", "my-skill", "1.0.0", 2L, "sha", true, "/api/v1/skills/global/my-skill/download"));
        mockMvc.perform(get("/api/v1/resolve/my-skill")
                        .param("version", "1.0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.match.version").value("1.0.0"))
                .andExpect(jsonPath("$.latestVersion.version").value("1.0.0"));
    }

    @Test
    void whoami_with_auth_returns_user_info() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-42",
                "tester",
                "tester@example.com",
                "https://example.com/avatar.png",
                "github",
                Set.of("SUPER_ADMIN")
        );
        var auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );

        mockMvc.perform(get("/api/v1/whoami")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.handle").value("user-42"))
                .andExpect(jsonPath("$.user.displayName").value("tester"))
                .andExpect(jsonPath("$.user.image").value("https://example.com/avatar.png"));
    }

    @Test
    void publish_skill_with_canonical_slug_routes_to_namespace_publish() throws Exception {
        SkillVersion version = publishVersion("1.0.0", 34L);
        given(skillPublishService.publishFromEntries(
                eq("team-ai"),
                anyList(),
                eq("user-42"),
                eq(SkillVisibility.PUBLIC),
                eq(Set.of("SUPER_ADMIN")),
                eq(false)))
                .willReturn(new SkillPublishService.PublishResult(12L, "my-skill", version));

        mockMvc.perform(multipart("/api/v1/skills")
                        .file(skillMdFile())
                        .param("payload", """
                                {"slug":"team-ai--my-skill","displayName":"My Skill","version":"1.0.0","acceptLicenseTerms":true,"tags":["latest"]}
                                """)
                        .with(authentication(superAdminAuth()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.skillId").value("12"))
                .andExpect(jsonPath("$.versionId").value("34"));
    }

    @Test
    void publish_skill_with_plain_slug_defaults_to_global_namespace() throws Exception {
        SkillVersion version = publishVersion("1.0.0", 35L);
        given(skillPublishService.publishFromEntries(
                eq("global"),
                anyList(),
                eq("user-42"),
                eq(SkillVisibility.PUBLIC),
                eq(Set.of("SUPER_ADMIN")),
                eq(false)))
                .willReturn(new SkillPublishService.PublishResult(13L, "my-skill", version));

        mockMvc.perform(multipart("/api/v1/skills")
                        .file(skillMdFile())
                        .param("payload", """
                                {"slug":"my-skill","displayName":"My Skill","version":"1.0.0","acceptLicenseTerms":true,"tags":["latest"]}
                                """)
                        .with(authentication(superAdminAuth()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.skillId").value("13"))
                .andExpect(jsonPath("$.versionId").value("35"));
    }

    @Test
    void publish_skill_with_payload_namespace_uses_explicit_namespace() throws Exception {
        SkillVersion version = publishVersion("1.0.0", 36L);
        given(skillPublishService.publishFromEntries(
                eq("team-explicit"),
                anyList(),
                eq("user-42"),
                eq(SkillVisibility.PUBLIC),
                eq(Set.of("SUPER_ADMIN")),
                eq(false)))
                .willReturn(new SkillPublishService.PublishResult(14L, "my-skill", version));

        mockMvc.perform(multipart("/api/v1/skills")
                        .file(skillMdFile())
                        .param("payload", """
                                {"namespace":"@team-explicit","slug":"my-skill","displayName":"My Skill","version":"1.0.0","acceptLicenseTerms":true,"tags":["latest"]}
                                """)
                        .with(authentication(superAdminAuth()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.skillId").value("14"))
                .andExpect(jsonPath("$.versionId").value("36"));
    }

    private CompatSkillLookupService.CompatSkillContext legacyCompatContext(String namespaceSlug, String skillSlug) {
        Namespace namespace = new Namespace(namespaceSlug, namespaceSlug, "tester");
        Skill skill = new Skill(1L, skillSlug, "tester", SkillVisibility.PUBLIC);
        return new CompatSkillLookupService.CompatSkillContext(namespace, skill, Optional.empty());
    }

    private MockMultipartFile skillMdFile() {
        return new MockMultipartFile(
                "files",
                "SKILL.md",
                "text/markdown",
                """
                ---
                name: my-skill
                description: Demo skill
                version: 1.0.0
                ---
                """.getBytes(StandardCharsets.UTF_8)
        );
    }

    private SkillVersion publishVersion(String versionValue, long versionId) {
        SkillVersion version = new SkillVersion(12L, versionValue, "user-42");
        version.setStatus(SkillVersionStatus.PENDING_REVIEW);
        ReflectionTestUtils.setField(version, "id", versionId);
        return version;
    }

    private UsernamePasswordAuthenticationToken superAdminAuth() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-42",
                "tester",
                "tester@example.com",
                "https://example.com/avatar.png",
                "github",
                Set.of("SUPER_ADMIN")
        );
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );
    }
}
