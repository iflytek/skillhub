package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.MessageResponse;
import com.iflytek.skillhub.dto.SkillSuiteCreateRequest;
import com.iflytek.skillhub.dto.SkillSuiteResponse;
import com.iflytek.skillhub.dto.SkillSuiteReviewRequest;
import com.iflytek.skillhub.dto.SkillSuiteReasonRequest;
import com.iflytek.skillhub.dto.SkillSuiteInstallPlanResponse;
import com.iflytek.skillhub.dto.SkillSuiteMemberCandidateResponse;
import com.iflytek.skillhub.dto.SkillSuiteVersionSummaryResponse;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.service.SkillSuiteAppService;
import com.iflytek.skillhub.ratelimit.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.List;

/** Transport-only endpoints for Suite creation, publication, and review decisions. */
@RestController
@Tag(name = "Skill Suites")
@RequestMapping({"/api/v1/suites", "/api/web/suites"})
public class SkillSuiteController extends BaseApiController {

    private final SkillSuiteAppService appService;

    public SkillSuiteController(SkillSuiteAppService appService, ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.appService = appService;
    }

    @GetMapping("/{namespace}/{slug}")
    @Operation(operationId = "getSkillSuite", summary = "Get one visible Suite version")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Suite version returned")
    public ApiResponse<SkillSuiteResponse> getDetail(
            @PathVariable String namespace,
            @PathVariable String slug,
            @RequestParam(required = false) String version,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.read", appService.getDetail(
                namespace, slug, version, userId, roles(roles), platformRoles(principal)));
    }

    @GetMapping("/{namespace}/{slug}/versions")
    @Operation(operationId = "listSkillSuiteVersions", summary = "List visible Suite versions")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Suite version history returned")
    public ApiResponse<List<SkillSuiteVersionSummaryResponse>> listVersions(
            @PathVariable String namespace,
            @PathVariable String slug,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.read", appService.listVersions(
                namespace, slug, userId, roles(roles), platformRoles(principal)));
    }

    @GetMapping("/member-candidates")
    @Operation(operationId = "searchSkillSuiteMemberCandidates", summary = "Search exact Skill versions eligible for a Suite draft")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Eligible member candidates returned")
    public ApiResponse<List<SkillSuiteMemberCandidateResponse>> searchCandidates(
            @RequestParam String suiteNamespace,
            @RequestParam SkillVisibility visibility,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.read", appService.searchCandidates(
                suiteNamespace, visibility, q, size, userId, roles(roles), platformRoles(principal)));
    }

    @PostMapping("/{namespace}/{slug}/install-plan")
    @Operation(operationId = "createSkillSuiteInstallPlan", summary = "Issue an idempotent exact-member Suite install plan")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Install plan issued")
    @RateLimit(category = "download", authenticated = 120, anonymous = 30)
    public ApiResponse<SkillSuiteInstallPlanResponse> createInstallPlan(
            @PathVariable String namespace,
            @PathVariable String slug,
            @RequestParam(required = false) String version,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String clientRequestId,
            HttpServletRequest request
    ) {
        return ok("response.success.read", appService.createInstallPlan(
                namespace, slug, version, userId, roles(roles), platformRoles(principal),
                clientRequestId, request));
    }

    @PostMapping
    @Operation(operationId = "createSkillSuite", summary = "Create a Suite and its first draft version")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Suite draft created")
    public ApiResponse<SkillSuiteResponse> create(
            @Valid @RequestBody SkillSuiteCreateRequest request,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        return ok("response.success.created", appService.create(
                request, userId, roles(roles), platformRoles(principal), httpRequest));
    }

    @PostMapping("/{suiteId}/versions")
    @Operation(operationId = "createSkillSuiteVersion", summary = "Create a new draft version for a Suite")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Suite version draft created")
    public ApiResponse<SkillSuiteResponse> createVersion(
            @PathVariable Long suiteId,
            @Valid @RequestBody SkillSuiteCreateRequest request,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        return ok("response.success.created", appService.createVersion(
                suiteId, request, userId, roles(roles), platformRoles(principal), httpRequest));
    }

    @PutMapping("/{suiteId}/versions/{versionId}")
    @Operation(operationId = "updateSkillSuiteDraft", summary = "Update an editable Suite draft")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Suite draft updated")
    public ApiResponse<SkillSuiteResponse> updateDraft(
            @PathVariable Long suiteId,
            @PathVariable Long versionId,
            @Valid @RequestBody SkillSuiteCreateRequest request,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        return ok("response.success.updated", appService.updateDraft(
                suiteId, versionId, request, userId, roles(roles), platformRoles(principal), httpRequest));
    }

    @PostMapping("/{suiteId}/versions/{versionId}/submit")
    @Operation(operationId = "submitSkillSuiteReview", summary = "Submit a public or namespace Suite draft for review")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Suite draft submitted")
    public ApiResponse<MessageResponse> submit(
            @PathVariable Long suiteId,
            @PathVariable Long versionId,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest request
    ) {
        appService.submitForReview(
                suiteId, versionId, userId, roles(roles), platformRoles(principal), request);
        return ok("response.success.updated", new MessageResponse("Suite submitted for review"));
    }

    @PostMapping("/{suiteId}/versions/{versionId}/publish")
    @Operation(operationId = "publishPrivateSkillSuite", summary = "Publish a private Suite draft directly")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Private Suite published")
    public ApiResponse<MessageResponse> publishPrivate(
            @PathVariable Long suiteId,
            @PathVariable Long versionId,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest request
    ) {
        appService.publishPrivate(
                suiteId, versionId, userId, roles(roles), platformRoles(principal), request);
        return ok("response.success.updated", new MessageResponse("Suite published"));
    }

    @PostMapping("/reviews/{reviewTaskId}/approve")
    @Operation(operationId = "approveSkillSuiteReview", summary = "Approve a pending Suite review")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Suite review approved")
    public ApiResponse<MessageResponse> approve(
            @PathVariable Long reviewTaskId,
            @Valid @RequestBody(required = false) SkillSuiteReviewRequest body,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest request
    ) {
        appService.approve(
                reviewTaskId, body == null ? null : body.comment(), userId,
                roles(roles), platformRoles(principal), request);
        return ok("response.success.updated", new MessageResponse("Suite review approved"));
    }

    @PostMapping("/reviews/{reviewTaskId}/reject")
    @Operation(operationId = "rejectSkillSuiteReview", summary = "Reject a pending Suite review")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Suite review rejected")
    public ApiResponse<MessageResponse> reject(
            @PathVariable Long reviewTaskId,
            @Valid @RequestBody(required = false) SkillSuiteReviewRequest body,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest request
    ) {
        appService.reject(
                reviewTaskId, body == null ? null : body.comment(), userId,
                roles(roles), platformRoles(principal), request);
        return ok("response.success.updated", new MessageResponse("Suite review rejected"));
    }

    @PostMapping("/{suiteId}/versions/{versionId}/reopen")
    @Operation(operationId = "reopenSkillSuiteDraft", summary = "Reopen a rejected Suite version as a draft")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Suite draft reopened")
    public ApiResponse<MessageResponse> reopen(
            @PathVariable Long suiteId,
            @PathVariable Long versionId,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest request
    ) {
        appService.reopen(
                suiteId, versionId, userId, roles(roles), platformRoles(principal), request);
        return ok("response.success.updated", new MessageResponse("Suite draft reopened"));
    }

    @PostMapping("/{suiteId}/versions/{versionId}/yank")
    @Operation(operationId = "yankSkillSuiteVersion", summary = "Yank a published Suite version")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Suite version yanked")
    public ApiResponse<MessageResponse> yank(
            @PathVariable Long suiteId,
            @PathVariable Long versionId,
            @Valid @RequestBody SkillSuiteReasonRequest body,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest request
    ) {
        appService.yank(
                suiteId, versionId, body.reason(), userId,
                roles(roles), platformRoles(principal), request);
        return ok("response.success.updated", new MessageResponse("Suite version yanked"));
    }

    @PostMapping("/{suiteId}/hide")
    @Operation(operationId = "hideSkillSuite", summary = "Hide a Suite from discovery")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Suite hidden")
    public ApiResponse<MessageResponse> hide(
            @PathVariable Long suiteId,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest request
    ) {
        appService.setHidden(suiteId, true, userId, roles(roles), platformRoles(principal), request);
        return ok("response.success.updated", new MessageResponse("Suite hidden"));
    }

    @PostMapping("/{suiteId}/restore")
    @Operation(operationId = "restoreSkillSuite", summary = "Restore a hidden Suite")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Suite restored")
    public ApiResponse<MessageResponse> restore(
            @PathVariable Long suiteId,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest request
    ) {
        appService.setHidden(suiteId, false, userId, roles(roles), platformRoles(principal), request);
        return ok("response.success.updated", new MessageResponse("Suite restored"));
    }

    @PostMapping("/{suiteId}/archive")
    @Operation(operationId = "archiveSkillSuite", summary = "Archive a Suite container")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Suite archived")
    public ApiResponse<MessageResponse> archive(
            @PathVariable Long suiteId,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest request
    ) {
        appService.setArchived(suiteId, true, userId, roles(roles), platformRoles(principal), request);
        return ok("response.success.updated", new MessageResponse("Suite archived"));
    }

    @PostMapping("/{suiteId}/unarchive")
    @Operation(operationId = "unarchiveSkillSuite", summary = "Restore an archived Suite container")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Suite unarchived")
    public ApiResponse<MessageResponse> unarchive(
            @PathVariable Long suiteId,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest request
    ) {
        appService.setArchived(suiteId, false, userId, roles(roles), platformRoles(principal), request);
        return ok("response.success.updated", new MessageResponse("Suite unarchived"));
    }

    @DeleteMapping("/{suiteId}")
    @Operation(operationId = "deleteSkillSuite", summary = "Delete a Suite without changing member Skills")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Suite deleted")
    public ApiResponse<MessageResponse> delete(
            @PathVariable Long suiteId,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest request
    ) {
        appService.delete(suiteId, userId, roles(roles), platformRoles(principal), request);
        return ok("response.success.updated", new MessageResponse("Suite deleted"));
    }

    private Map<Long, NamespaceRole> roles(Map<Long, NamespaceRole> roles) {
        return roles == null ? Map.of() : roles;
    }

    private Set<String> platformRoles(PlatformPrincipal principal) {
        return principal == null || principal.platformRoles() == null
                ? Set.of()
                : principal.platformRoles();
    }
}
