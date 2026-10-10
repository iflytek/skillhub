package com.iflytek.skillhub.controller.cli;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CliAuthControllerTest {
    @Autowired MockMvc mockMvc;

    @Test
    void whoamiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/cli/v1/auth/whoami"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void whoamiReturnsCliPrincipal() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-7", "cli-user", "cli@example.com", "", "api_token", Set.of("USER"));
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        mockMvc.perform(get("/api/cli/v1/auth/whoami").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handle").value("user-7"))
                .andExpect(jsonPath("$.data.displayName").value("cli-user"))
                .andExpect(jsonPath("$.data.email").value("cli@example.com"));
    }
}
