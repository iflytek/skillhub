package com.iflytek.skillhub.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.governance.GovernanceNotificationService;
import com.iflytek.skillhub.domain.governance.UserNotification;
import com.iflytek.skillhub.dto.GovernanceActivityItemResponse;
import com.iflytek.skillhub.dto.GovernanceInboxItemResponse;
import com.iflytek.skillhub.dto.GovernanceNotificationResponse;
import com.iflytek.skillhub.dto.GovernanceSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.service.GovernanceWorkbenchAppService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GovernanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GovernanceWorkbenchAppService governanceWorkbenchAppService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @MockBean
    private RbacService rbacService;

    @MockBean
    private GovernanceNotificationService governanceNotificationService;

    @Test
    void summary_returnsGovernanceSummary() throws Exception {
        when(rbacService.getUserRoleCodes("admin")).thenReturn(Set.of("SKILL_ADMIN"));
        when(governanceWorkbenchAppService.getSummary("admin", Map.of(), Set.of("SKILL_ADMIN")))
                .thenReturn(new GovernanceSummaryResponse(3, 2, 1, 4));

        mockMvc.perform(get("/api/v1/governance/summary").with(auth("admin", Set.of("SKILL_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingReviews").value(3))
                .andExpect(jsonPath("$.data.pendingPromotions").value(2))
                .andExpect(jsonPath("$.data.pendingReports").value(1))
                .andExpect(jsonPath("$.data.unreadNotifications").value(4));
    }

    @Test
    void inbox_returnsUnifiedItems() throws Exception {
        when(rbacService.getUserRoleCodes("admin")).thenReturn(Set.of("SKILL_ADMIN"));
        when(governanceWorkbenchAppService.listInbox("admin", Map.of(), Set.of("SKILL_ADMIN"), null, 0, 20))
                .thenReturn(new PageResponse<>(
                        List.of(new GovernanceInboxItemResponse(
                                "REVIEW",
                                1L,
                                "team-a/skill-a@1.0.0",
                                "Pending review",
                                "2026-03-16T02:00:00Z",
                                "team-a",
                                "skill-a"
                        )),
                        1,
                        0,
                        20
                ));

        mockMvc.perform(get("/api/v1/governance/inbox").with(auth("admin", Set.of("SKILL_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].type").value("REVIEW"));
    }

    @Test
    void activity_returnsGovernanceActivity() throws Exception {
        when(rbacService.getUserRoleCodes("admin")).thenReturn(Set.of("SKILL_ADMIN"));
        when(governanceWorkbenchAppService.listActivity(Set.of("SKILL_ADMIN"), 0, 20))
                .thenReturn(new PageResponse<>(
                        List.of(new GovernanceActivityItemResponse(
                                1L,
                                "REVIEW_APPROVE",
                                "admin",
                                "Admin",
                                "REVIEW_TASK",
                                "99",
                                "{\"comment\":\"LGTM\"}",
                                Instant.parse("2026-03-16T02:00:00Z").toString()
                        )),
                        1,
                        0,
                        20
                ));

        mockMvc.perform(get("/api/v1/governance/activity").with(auth("admin", Set.of("SKILL_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].action").value("REVIEW_APPROVE"));
    }

    @Test
    void notifications_returnsCurrentUserNotifications() throws Exception {
        UserNotification notification = new UserNotification(
                "admin",
                "REVIEW",
                "REVIEW_TASK",
                99L,
                "Review approved",
                "{}",
                Instant.parse("2026-03-18T00:00:00Z"));
        when(governanceNotificationService.listNotifications("admin", 0, 20))
                .thenReturn(new PageImpl<>(List.of(notification), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/governance/notifications").with(auth("admin", Set.of("SKILL_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].category").value("REVIEW"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void notifications_remainUtcAcrossJvmDefaultTimeZones() throws Exception {
        UserNotification notification = new UserNotification(
                "admin",
                "REVIEW",
                "REVIEW_TASK",
                99L,
                "Review approved",
                "{}",
                Instant.parse("2026-03-18T00:00:00Z"));
        when(governanceNotificationService.listNotifications("admin", 0, 20))
                .thenReturn(new PageImpl<>(List.of(notification), PageRequest.of(0, 20), 1));

        TimeZone original = TimeZone.getDefault();
        try {
            for (String zoneId : List.of("Asia/Shanghai", "America/Los_Angeles")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
                mockMvc.perform(get("/api/v1/governance/notifications").with(auth("admin", Set.of("SKILL_ADMIN"))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.items[0].createdAt").value("2026-03-18T00:00:00Z"));
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void markRead_returnsUpdatedNotification() throws Exception {
        UserNotification notification = new UserNotification(
                "admin",
                "REVIEW",
                "REVIEW_TASK",
                99L,
                "Review approved",
                "{}",
                Instant.parse("2026-03-18T00:00:00Z"));
        when(governanceNotificationService.markRead(10L, "admin")).thenReturn(notification);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/governance/notifications/10/read")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .with(auth("admin", Set.of("SKILL_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value("REVIEW"));
    }

    private RequestPostProcessor auth(String userId, Set<String> roles) {
        PlatformPrincipal principal = new PlatformPrincipal(
                userId,
                userId,
                userId + "@example.com",
                "",
                "session",
                roles
        );
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList()
        );
        return authentication(authenticationToken);
    }
}
