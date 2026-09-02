package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.dto.AdminSkillActionRequest;
import com.iflytek.skillhub.dto.AdminSkillMutationResponse;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.SkillLifecycleMutationResponse;
import com.iflytek.skillhub.domain.skill.service.SkillGovernanceService;
import com.iflytek.skillhub.domain.skill.service.SkillSlugResolutionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative skill-governance endpoints reserved for platform-level
 * moderation actions such as hide and unhide.
 */
@RestController
@RequestMapping("/api/v1/admin/skills")
public class AdminSkillController extends BaseApiController {

    private final SkillGovernanceService skillGovernanceService;
    private final NamespaceRepository namespaceRepository;
    private final SkillSlugResolutionService skillSlugResolutionService;

    public AdminSkillController(ApiResponseFactory responseFactory,
                                SkillGovernanceService skillGovernanceService,
                                NamespaceRepository namespaceRepository,
                                SkillSlugResolutionService skillSlugResolutionService) {
        super(responseFactory);
        this.skillGovernanceService = skillGovernanceService;
        this.namespaceRepository = namespaceRepository;
        this.skillSlugResolutionService = skillSlugResolutionService;
    }

    @PostMapping("/{namespace}/{slug}/archive")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<SkillLifecycleMutationResponse> archiveSkill(@PathVariable String namespace,
                                                                     @PathVariable String slug,
                                                                     @RequestBody(required = false) AdminSkillActionRequest request,
                                                                     @AuthenticationPrincipal PlatformPrincipal principal,
                                                                     HttpServletRequest httpRequest) {
        Skill skill = findSkill(namespace, slug);
        Skill archived = skillGovernanceService.archiveSkillAsAdmin(
            skill.getId(),
            principal.userId(),
            httpRequest.getRemoteAddr(),
            httpRequest.getHeader("User-Agent"),
            request != null ? request.reason() : null
        );
        return ok("response.success.updated", new SkillLifecycleMutationResponse(
            archived.getId(), null, "ARCHIVE", archived.getStatus().name()));
    }

    @PostMapping("/{namespace}/{slug}/unarchive")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<SkillLifecycleMutationResponse> unarchiveSkill(@PathVariable String namespace,
                                                                       @PathVariable String slug,
                                                                       @AuthenticationPrincipal PlatformPrincipal principal,
                                                                       HttpServletRequest httpRequest) {
        Skill skill = findSkill(namespace, slug);
        Skill restored = skillGovernanceService.unarchiveSkillAsAdmin(
            skill.getId(),
            principal.userId(),
            httpRequest.getRemoteAddr(),
            httpRequest.getHeader("User-Agent")
        );
        return ok("response.success.updated", new SkillLifecycleMutationResponse(
            restored.getId(), null, "UNARCHIVE", restored.getStatus().name()));
    }

    @PostMapping("/{skillId}/hide")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<AdminSkillMutationResponse> hideSkill(@PathVariable Long skillId,
                                                             @RequestBody(required = false) AdminSkillActionRequest request,
                                                             @AuthenticationPrincipal PlatformPrincipal principal,
                                                             HttpServletRequest httpRequest) {
        var skill = skillGovernanceService.hideSkill(
            skillId,
            principal.userId(),
            httpRequest.getRemoteAddr(),
            httpRequest.getHeader("User-Agent"),
            request != null ? request.reason() : null
        );
        return ok("response.success.updated", new AdminSkillMutationResponse(skillId, null, "HIDE", skill.getStatus().name()));
    }

    @PostMapping("/{skillId}/unhide")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<AdminSkillMutationResponse> unhideSkill(@PathVariable Long skillId,
                                                               @AuthenticationPrincipal PlatformPrincipal principal,
                                                               HttpServletRequest httpRequest) {
        var skill = skillGovernanceService.unhideSkill(
            skillId,
            principal.userId(),
            httpRequest.getRemoteAddr(),
            httpRequest.getHeader("User-Agent")
        );
        return ok("response.success.updated", new AdminSkillMutationResponse(skillId, null, "UNHIDE", skill.getStatus().name()));
    }

    @PostMapping("/versions/{versionId}/yank")
    @PreAuthorize("hasAnyRole('SKILL_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<AdminSkillMutationResponse> yankVersion(@PathVariable Long versionId,
                                                               @RequestBody(required = false) AdminSkillActionRequest request,
                                                               @AuthenticationPrincipal PlatformPrincipal principal,
                                                               HttpServletRequest httpRequest) {
        var version = skillGovernanceService.yankVersion(
            versionId,
            principal.userId(),
            httpRequest.getRemoteAddr(),
            httpRequest.getHeader("User-Agent"),
            request != null ? request.reason() : null
        );
        return ok("response.success.updated", new AdminSkillMutationResponse(version.getSkillId(), versionId, "YANK", version.getStatus().name()));
    }

    private Skill findSkill(String namespaceSlug, String skillSlug) {
        String cleanNamespace = namespaceSlug.startsWith("@") ? namespaceSlug.substring(1) : namespaceSlug;
        Namespace namespace = namespaceRepository.findBySlug(cleanNamespace)
            .orElseThrow(() -> new DomainBadRequestException("error.namespace.slug.notFound", cleanNamespace));
        return skillSlugResolutionService.resolve(
            namespace.getId(), skillSlug, null, SkillSlugResolutionService.Preference.PUBLISHED);
    }
}
