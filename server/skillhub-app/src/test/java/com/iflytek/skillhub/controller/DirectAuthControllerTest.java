package com.iflytek.skillhub.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.local.LocalAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.security.AuthFailureThrottleService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "skillhub.auth.direct.enabled=true"
})
class DirectAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LocalAuthService localAuthService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private AuthFailureThrottleService authFailureThrottleService;

    @Test
    void directLoginShouldAuthenticateViaConfiguredProvider() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
            "usr_direct_1",
            "direct-user",
            null,
            null,
            "local",
            Set.of("USER")
        );
        given(localAuthService.login("direct-user", "Abcd123!")).willReturn(principal);
        given(namespaceMemberRepository.findByUserId("usr_direct_1")).willReturn(List.of());

        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/v1/auth/direct/login")
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {"provider":"local","username":"direct-user","password":"Abcd123!"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.userId").value("usr_direct_1"))
            .andReturn()
            .getRequest()
            .getSession(false);

        mockMvc.perform(get("/api/v1/auth/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value("usr_direct_1"));
    }

    @Test
    void directLoginShouldRejectUnsupportedProvider() throws Exception {
        mockMvc.perform(post("/api/v1/auth/direct/login")
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {"provider":"private-sso","username":"user","password":"pw"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }
}
