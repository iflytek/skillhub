package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.service.GithubReviewGroupAppService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/reviews/sync/groups", "/api/web/reviews/sync/groups"})
public class GithubReviewGroupController extends BaseApiController {
    private final GithubReviewGroupAppService groups;
    public GithubReviewGroupController(ApiResponseFactory responses, GithubReviewGroupAppService groups) {
        super(responses); this.groups = groups;
    }

    @GetMapping
    public ApiResponse<PageResponse<GithubReviewGroupResponse>> listGithubReviewGroups(
            @RequestParam(defaultValue = "PENDING") String status, @RequestParam(required = false) Long namespaceId,
            @RequestParam(defaultValue = "") String q, @RequestParam(required = false) String reviewKind,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "DESC") String sortDirection, @RequestAttribute("userId") String actor,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles) {
        return ok("response.success.read", groups.list(status, namespaceId, q, reviewKind, page, size, sortDirection, actor, roles));
    }

    @GetMapping("/{snapshotId}")
    public ApiResponse<GithubReviewGroupResponse> getGithubReviewGroup(@PathVariable long snapshotId,
            @RequestAttribute("userId") String actor,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles) {
        return ok("response.success.read", groups.describe(snapshotId, actor, roles));
    }

    @GetMapping("/{snapshotId}/members")
    public ApiResponse<PageResponse<GithubReviewGroupMemberResponse>> listGithubReviewGroupMembers(@PathVariable long snapshotId,
            @RequestParam(defaultValue = "PENDING") String status, @RequestParam(required = false) Long namespaceId,
            @RequestParam(defaultValue = "") String q, @RequestParam(required = false) String reviewKind,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "DESC") String sortDirection, @RequestAttribute("userId") String actor,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles) {
        return ok("response.success.read", groups.members(snapshotId, status, namespaceId, q, reviewKind,
                page, size, sortDirection, actor, roles));
    }
}
