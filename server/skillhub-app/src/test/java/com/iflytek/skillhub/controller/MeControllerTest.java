package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.SkillLifecycleVersionResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.service.MySkillAppService;
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

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class MeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @MockBean
    private MySkillAppService mySkillAppService;

    @Test
    void listMySkills_returns_paginated_items() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-42", "tester", "tester@example.com", "", "github", Set.of("USER")
        );
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        given(mySkillAppService.listMySkills("user-42", 1, 5, null, Set.of("USER")))
                .willReturn(new PageResponse<>(
                        List.of(new SkillSummaryResponse(
                                7L,
                                "copilot",
                                "Copilot",
                                "Assist with code review",
                                "PUBLIC",
                                "ACTIVE",
                                12L,
                                3,
                                null,
                                0,
                                "team-ai",
                                Instant.parse("2026-03-17T12:00:00Z"),
                                false,
                                new SkillLifecycleVersionResponse(11L, "1.0.0", "PUBLISHED"),
                                new SkillLifecycleVersionResponse(11L, "1.0.0", "PUBLISHED"),
                                null,
                                "PUBLISHED"
                        )),
                        9,
                        1,
                        5
                ));

        mockMvc.perform(get("/api/v1/me/skills")
                        .with(authentication(auth))
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].slug").value("copilot"))
                .andExpect(jsonPath("$.data.total").value(9))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(5));
    }

    @Test
    void listMySkills_forwardsFilterAndRoles() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-42", "tester", "tester@example.com", "", "github", Set.of("SUPER_ADMIN")
        );
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );

        given(mySkillAppService.listMySkills("user-42", 0, 10, "HIDDEN", Set.of("SUPER_ADMIN")))
                .willReturn(new PageResponse<>(List.of(), 0, 0, 10));

        mockMvc.perform(get("/api/v1/me/skills")
                        .with(authentication(auth))
                        .param("filter", "HIDDEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void listMyStars_returns_paginated_items() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-42", "tester", "tester@example.com", "", "github", Set.of("USER")
        );
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        given(mySkillAppService.listMyStars("user-42", 0, 12))
                .willReturn(new PageResponse<>(List.of(), 0, 0, 12));

        mockMvc.perform(get("/api/v1/me/stars").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(12));
    }
}
