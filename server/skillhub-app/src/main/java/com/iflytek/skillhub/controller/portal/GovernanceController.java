package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.governance.GovernanceNotificationService;
import com.iflytek.skillhub.domain.governance.UserNotification;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.GovernanceActivityItemResponse;
import com.iflytek.skillhub.dto.GovernanceInboxItemResponse;
import com.iflytek.skillhub.dto.GovernanceNotificationResponse;
import com.iflytek.skillhub.dto.GovernanceSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.service.GovernanceWorkbenchAppService;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portal endpoints that expose governance dashboards, inbox items, activity,
 * and user-facing governance notifications.
 */
@RestController
@RequestMapping({"/api/v1/governance", "/api/web/governance"})
public class GovernanceController extends BaseApiController {

    private final GovernanceWorkbenchAppService governanceWorkbenchAppService;
    private final RbacService rbacService;
    private final GovernanceNotificationService governanceNotificationService;

    public GovernanceController(GovernanceWorkbenchAppService governanceWorkbenchAppService,
                                RbacService rbacService,
                                GovernanceNotificationService governanceNotificationService,
                                ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.governanceWorkbenchAppService = governanceWorkbenchAppService;
        this.rbacService = rbacService;
        this.governanceNotificationService = governanceNotificationService;
    }

    @GetMapping("/summary")
    public ApiResponse<GovernanceSummaryResponse> summary(
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        return ok(
                "response.success.read",
                governanceWorkbenchAppService.getSummary(userId, userNsRoles != null ? userNsRoles : Map.of(), roles(userId))
        );
    }

    @GetMapping("/inbox")
    public ApiResponse<PageResponse<GovernanceInboxItemResponse>> inbox(
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok(
                "response.success.read",
                governanceWorkbenchAppService.listInbox(
                        userId,
                        userNsRoles != null ? userNsRoles : Map.of(),
                        roles(userId),
                        type,
                        page,
                        size
                )
        );
    }

    @GetMapping("/activity")
    public ApiResponse<PageResponse<GovernanceActivityItemResponse>> activity(
            @RequestAttribute("userId") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok("response.success.read", governanceWorkbenchAppService.listActivity(roles(userId), page, size));
    }

    @GetMapping("/notifications")
    public ApiResponse<PageResponse<GovernanceNotificationResponse>> notifications(
            @RequestAttribute("userId") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        org.springframework.data.domain.Page<UserNotification> notifications = governanceNotificationService.listNotifications(userId, page, size);
        return ok(
                "response.success.read",
                PageResponse.from(notifications.map(this::toNotificationResponse))
        );
    }

    @PostMapping("/notifications/{id}/read")
    public ApiResponse<GovernanceNotificationResponse> markNotificationRead(
            @PathVariable Long id,
            @RequestAttribute("userId") String userId) {
        return ok("response.success.updated", toNotificationResponse(governanceNotificationService.markRead(id, userId)));
    }

    private Set<String> roles(String userId) {
        return rbacService.getUserRoleCodes(userId);
    }

    private GovernanceNotificationResponse toNotificationResponse(UserNotification notification) {
        return new GovernanceNotificationResponse(
                notification.getId(),
                notification.getCategory(),
                notification.getEntityType(),
                notification.getEntityId(),
                notification.getTitle(),
                notification.getBodyJson(),
                notification.getStatus().name(),
                notification.getCreatedAt() != null ? notification.getCreatedAt().toString() : null,
                notification.getReadAt() != null ? notification.getReadAt().toString() : null
        );
    }
}
