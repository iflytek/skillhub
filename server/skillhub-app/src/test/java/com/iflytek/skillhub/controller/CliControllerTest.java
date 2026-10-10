package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
class CliControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @Test
    void whoamiShouldReturnUnauthorizedForAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/v1/whoami"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void whoamiShouldReturnCurrentPrincipal() throws Exception {
        given(namespaceMemberRepository.findByUserId("user-7")).willReturn(List.of());

        PlatformPrincipal principal = new PlatformPrincipal(
            "user-7",
            "cli-user",
            "cli@example.com",
            "",
            "api_token",
            Set.of("SKILL_ADMIN")
        );

        var auth = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_SKILL_ADMIN"))
        );

        mockMvc.perform(get("/api/v1/whoami").with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.handle").value("user-7"))
            .andExpect(jsonPath("$.user.displayName").value("cli-user"))
            .andExpect(jsonPath("$.user.image").value(""));
    }
}
