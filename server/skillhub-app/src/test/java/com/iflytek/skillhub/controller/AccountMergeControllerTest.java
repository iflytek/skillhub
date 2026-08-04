package com.iflytek.skillhub.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountMergeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @Test
    void initiate_failsClosedWithoutReturningSecondaryAccountProof() throws Exception {
        mockMvc.perform(post("/api/v1/account/merge/initiate")
                .with(authentication(primaryAuthentication()))
                .with(csrf())
                .locale(Locale.ENGLISH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"secondaryIdentifier":"secondary"}
                    """))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value(503))
            .andExpect(jsonPath("$.msg").value(
                "Account merging is temporarily unavailable while the ownership verification flow is being secured"
            ))
            .andExpect(jsonPath("$.data.verificationToken").doesNotExist())
            .andExpect(jsonPath("$.data.secondaryUserId").doesNotExist())
            .andExpect(jsonPath("$.data.mergeRequestId").doesNotExist());
    }

    @Test
    void verify_failsClosedWithTheSameStableError() throws Exception {
        mockMvc.perform(post("/api/v1/account/merge/verify")
                .with(authentication(primaryAuthentication()))
                .with(csrf())
                .locale(Locale.ENGLISH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mergeRequestId":1,"verificationToken":"merge-token"}
                    """))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value(503))
            .andExpect(jsonPath("$.msg").value(
                "Account merging is temporarily unavailable while the ownership verification flow is being secured"
            ));
    }

    @Test
    void confirm_failsClosedWithTheSameStableError() throws Exception {
        mockMvc.perform(post("/api/v1/account/merge/confirm")
                .with(authentication(primaryAuthentication()))
                .with(csrf())
                .locale(Locale.ENGLISH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mergeRequestId":1}
                    """))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value(503))
            .andExpect(jsonPath("$.msg").value(
                "Account merging is temporarily unavailable while the ownership verification flow is being secured"
            ));
    }

    @Test
    void initiate_withoutCsrf_isRejectedByTheExistingSecurityChain() throws Exception {
        mockMvc.perform(post("/api/v1/account/merge/initiate")
                .with(authentication(primaryAuthentication()))
                .with(csrf().useInvalidToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"secondaryIdentifier":"secondary"}
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void initiate_withoutAuthentication_remainsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/account/merge/initiate")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"secondaryIdentifier":"secondary"}
                    """))
            .andExpect(status().isUnauthorized());
    }

    private UsernamePasswordAuthenticationToken primaryAuthentication() {
        PlatformPrincipal principal = new PlatformPrincipal(
            "usr_primary",
            "primary",
            "p@example.com",
            "",
            "local",
            Set.of()
        );
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }
}
