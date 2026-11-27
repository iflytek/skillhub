package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.GithubRepositoryInstallPlanResponse;
import com.iflytek.skillhub.repository.GithubRepositoryCatalogQueryRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GithubRepositoryInstallControllerTest {
    @Autowired MockMvc mvc;
    @MockBean GithubRepositoryCatalogQueryRepository catalog;
    @MockBean RbacService rbac;
    @MockBean NamespaceMemberRepository namespaceMembers;

    @Test
    void publicAndReadTokenRequestsUseTheSameReadOnlyPlan() throws Exception {
        var member = new GithubRepositoryInstallPlanResponse.Member(1, "github", "alpha", "Alpha", "1.0.0", 2,
                "sha256:" + "a".repeat(64), "/api/cli/v1/skills/github/alpha/versions/1.0.0/download");
        when(catalog.installPlan(eq(42L), anySet())).thenReturn(Optional.of(
                new GithubRepositoryInstallPlanResponse("42", "owner", "repo", List.of(member))));
        for (String prefix : List.of("/api/web", "/api/cli/v1")) {
            mvc.perform(get(prefix + "/github-repositories/42/install-plan"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.members[0].versionId").value(2));
            mvc.perform(get(prefix + "/github-repositories/42/install-plan").with(token("skill:read")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.repositoryId").value("42"));
        }
    }

    @Test
    void tokenWithoutReadUsesPublicViewEvenWhenCallerIsATeamMember() throws Exception {
        when(namespaceMembers.findByUserId("reader"))
                .thenReturn(List.of(new NamespaceMember(7L, "reader", NamespaceRole.MEMBER)));
        when(catalog.installPlan(42L, Set.of())).thenReturn(Optional.empty());
        when(catalog.installPlan(42L, Set.of(7L))).thenReturn(Optional.of(
                new GithubRepositoryInstallPlanResponse("42", "owner", "repo", List.of())));

        mvc.perform(get("/api/web/github-repositories/42/install-plan").with(token("skill:submit")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/cli/v1/github-repositories/42/install-plan").with(token("skill:read")))
                .andExpect(status().isOk());
        verify(catalog).installPlan(42L, Set.of());
        verify(catalog).installPlan(42L, Set.of(7L));
    }

    @Test
    void inaccessibleAndInvalidIdentifiersDoNotExposeRepositoryData() throws Exception {
        when(catalog.installPlan(eq(42L), anySet())).thenReturn(Optional.empty());
        mvc.perform(get("/api/web/github-repositories/42/install-plan"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/cli/v1/github-repositories/not-a-number/install-plan"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportsActualApplicationOpenApiWhenRequested() throws Exception {
        String output = System.getProperty("skillhub.openapi.output");
        if (output != null) {
            byte[] json = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();
            Files.write(Path.of(output), json);
        }
    }

    private RequestPostProcessor token(String capability) {
        return authentication(new UsernamePasswordAuthenticationToken(
                new PlatformPrincipal("reader", "Reader", null, null, "api_token", Set.of(), Set.of(capability)), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("SCOPE_" + capability))));
    }
}
