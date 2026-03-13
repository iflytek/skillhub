package com.iflytek.skillhub.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.merge.AccountMergeService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountMergeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountMergeService accountMergeService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @Test
    void initiate_returnsVerificationToken() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal("usr_primary", "primary", "p@example.com", "", "local", Set.of());
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        given(accountMergeService.initiate("usr_primary", "secondary"))
            .willReturn(new AccountMergeService.InitiationResult(1L, "usr_secondary", "merge-token", LocalDateTime.parse("2026-03-12T22:30:00")));

        mockMvc.perform(post("/api/v1/account/merge/initiate")
                .with(authentication(auth))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"secondaryIdentifier":"secondary"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.mergeRequestId").value(1))
            .andExpect(jsonPath("$.data.secondaryUserId").value("usr_secondary"))
            .andExpect(jsonPath("$.data.verificationToken").value("merge-token"));
    }

    @Test
    void verify_returnsSuccessMessage() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal("usr_primary", "primary", "p@example.com", "", "local", Set.of());
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));

        mockMvc.perform(post("/api/v1/account/merge/verify")
                .with(authentication(auth))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mergeRequestId":1,"verificationToken":"merge-token"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.message").value("Account merge completed"));
    }
}
