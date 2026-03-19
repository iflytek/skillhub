package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.AdminSkillActionRequest;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.SkillLifecycleMutationResponse;
import com.iflytek.skillhub.dto.SkillVersionRereleaseRequest;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.SkillLifecycleAppService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints that mutate skill lifecycle state, including archive, unarchive,
 * withdraw-review, delete-version, and rerelease operations.
 */
@RestController
@RequestMapping({"/api/v1/skills", "/api/web/skills"})
public class SkillLifecycleController extends BaseApiController {

    private final SkillLifecycleAppService skillLifecycleAppService;

    public SkillLifecycleController(SkillLifecycleAppService skillLifecycleAppService,
                                    ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.skillLifecycleAppService = skillLifecycleAppService;
    }

    @PostMapping("/{namespace}/{slug}/archive")
    public ApiResponse<SkillLifecycleMutationResponse> archiveSkill(@PathVariable String namespace,
                                                                    @PathVariable String slug,
                                                                    @RequestBody(required = false) AdminSkillActionRequest request,
                                                                    @RequestAttribute("userId") String userId,
                                                                    @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
                                                                    HttpServletRequest httpRequest) {
        return ok("response.success.updated",
                skillLifecycleAppService.archiveSkill(
                        namespace,
                        slug,
                        request,
                        userId,
                        userNsRoles,
                        AuditRequestContext.from(httpRequest)));
    }

    @PostMapping("/{namespace}/{slug}/unarchive")
    public ApiResponse<SkillLifecycleMutationResponse> unarchiveSkill(@PathVariable String namespace,
                                                                      @PathVariable String slug,
                                                                      @RequestAttribute("userId") String userId,
                                                                      @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
                                                                      HttpServletRequest httpRequest) {
        return ok("response.success.updated",
                skillLifecycleAppService.unarchiveSkill(
                        namespace,
                        slug,
                        userId,
                        userNsRoles,
                        AuditRequestContext.from(httpRequest)));
    }

    @DeleteMapping("/{namespace}/{slug}/versions/{version}")
    public ApiResponse<SkillLifecycleMutationResponse> deleteVersion(@PathVariable String namespace,
                                                                     @PathVariable String slug,
                                                                     @PathVariable String version,
                                                                     @RequestAttribute("userId") String userId,
                                                                     @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
                                                                     HttpServletRequest httpRequest) {
        return ok("response.success.deleted",
                skillLifecycleAppService.deleteVersion(
                        namespace,
                        slug,
                        version,
                        userId,
                        userNsRoles,
                        AuditRequestContext.from(httpRequest)));
    }

    @PostMapping("/{namespace}/{slug}/versions/{version}/withdraw-review")
    public ApiResponse<SkillLifecycleMutationResponse> withdrawReview(@PathVariable String namespace,
                                                                     @PathVariable String slug,
                                                                     @PathVariable String version,
                                                                     @RequestAttribute("userId") String userId,
                                                                     HttpServletRequest httpRequest) {
        return ok("response.success.updated",
                skillLifecycleAppService.withdrawReview(
                        namespace,
                        slug,
                        version,
                        userId,
                        AuditRequestContext.from(httpRequest)));
    }

    @PostMapping("/{namespace}/{slug}/versions/{version}/rerelease")
    public ApiResponse<SkillLifecycleMutationResponse> rereleaseVersion(@PathVariable String namespace,
                                                                        @PathVariable String slug,
                                                                        @PathVariable String version,
                                                                        @Valid @RequestBody SkillVersionRereleaseRequest request,
                                                                        @RequestAttribute("userId") String userId,
                                                                        @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
                                                                        HttpServletRequest httpRequest) {
        return ok("response.success.updated",
                skillLifecycleAppService.rereleaseVersion(
                        namespace,
                        slug,
                        version,
                        request,
                        userId,
                        userNsRoles,
                        AuditRequestContext.from(httpRequest)));
    }
}
