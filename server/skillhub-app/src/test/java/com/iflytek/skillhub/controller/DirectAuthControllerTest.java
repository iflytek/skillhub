package com.iflytek.skillhub.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.local.LocalCredentialRepository;
import com.iflytek.skillhub.auth.local.LocalAuthService;
import com.iflytek.skillhub.auth.settings.LocalAuthSettings;
import com.iflytek.skillhub.auth.settings.LocalAuthSettingsService;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
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

    @MockBean
    private UserAccountRepository userAccountRepository;

    @MockBean
    private UserRoleBindingRepository userRoleBindingRepository;

    @MockBean
    private LocalCredentialRepository localCredentialRepository;

    @MockBean
    private LocalAuthSettingsService localAuthSettingsService;

    @org.junit.jupiter.api.BeforeEach
    void localAuthEnabled() {
        given(localAuthSettingsService.current()).willReturn(new LocalAuthSettings(1L, true, true, 0L, null));
    }

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
        given(userAccountRepository.findById("usr_direct_1"))
            .willReturn(java.util.Optional.of(new UserAccount("usr_direct_1", "direct-user", null, null)));
        given(userRoleBindingRepository.findByUserId("usr_direct_1")).willReturn(List.of());
        given(localCredentialRepository.existsByUserId("usr_direct_1")).willReturn(true);

        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/v1/auth/direct/login")
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {"provider":"local","username":"direct-user","password":"Abcd123!"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.userId").value("usr_direct_1"))
            .andExpect(jsonPath("$.data.canChangePassword").value(true))
            .andReturn()
            .getRequest()
            .getSession(false);

        mockMvc.perform(get("/api/v1/auth/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.userId").value("usr_direct_1"))
            .andExpect(jsonPath("$.data.canChangePassword").value(true));
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
