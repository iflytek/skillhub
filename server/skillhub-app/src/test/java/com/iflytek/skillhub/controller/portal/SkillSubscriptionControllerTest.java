package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.social.SkillSubscriptionService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SkillSubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SkillSubscriptionService skillSubscriptionService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    private static UsernamePasswordAuthenticationToken authenticatedUser() {
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

    // --- PUT /api/web/skills/{skillId}/subscription ---

    @Test
    void subscribe_skill_returns_envelope() throws Exception {
        mockMvc.perform(put("/api/web/skills/10/subscription")
                        .with(authentication(authenticatedUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        verify(skillSubscriptionService).subscribe(eq(10L), eq("user-42"));
    }

    @Test
    void subscribe_skill_unauthenticated_returns_401() throws Exception {
        mockMvc.perform(put("/api/web/skills/10/subscription"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    // --- DELETE /api/web/skills/{skillId}/subscription ---

    @Test
    void unsubscribe_skill_returns_envelope() throws Exception {
        mockMvc.perform(delete("/api/web/skills/10/subscription")
                        .with(authentication(authenticatedUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        verify(skillSubscriptionService).unsubscribe(eq(10L), eq("user-42"));
    }

    @Test
    void unsubscribe_skill_unauthenticated_returns_401() throws Exception {
        mockMvc.perform(delete("/api/web/skills/10/subscription"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    // --- GET /api/web/skills/{skillId}/subscription ---

    @Test
    void check_subscribed_returns_true() throws Exception {
        when(skillSubscriptionService.isSubscribed(eq(10L), eq("user-42"))).thenReturn(true);

        mockMvc.perform(get("/api/web/skills/10/subscription")
                        .with(authentication(authenticatedUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void check_subscribed_returns_false() throws Exception {
        when(skillSubscriptionService.isSubscribed(eq(10L), eq("user-42"))).thenReturn(false);

        mockMvc.perform(get("/api/web/skills/10/subscription")
                        .with(authentication(authenticatedUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(false))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void check_subscribed_unauthenticated_returns_false() throws Exception {
        mockMvc.perform(get("/api/web/skills/10/subscription"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(false));
    }
}
