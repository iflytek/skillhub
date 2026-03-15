package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.review.ReviewService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.service.SkillGovernanceService;
import com.iflytek.skillhub.dto.AdminSkillActionRequest;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.SkillLifecycleMutationResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/skills", "/api/web/skills"})
public class SkillLifecycleController extends BaseApiController {

    private final NamespaceRepository namespaceRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillGovernanceService skillGovernanceService;
    private final ReviewService reviewService;
    private final AuditLogService auditLogService;

    public SkillLifecycleController(NamespaceRepository namespaceRepository,
                                    SkillRepository skillRepository,
                                    SkillVersionRepository skillVersionRepository,
                                    SkillGovernanceService skillGovernanceService,
                                    ReviewService reviewService,
                                    AuditLogService auditLogService,
                                    ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.namespaceRepository = namespaceRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillGovernanceService = skillGovernanceService;
        this.reviewService = reviewService;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/{namespace}/{slug}/archive")
    public ApiResponse<SkillLifecycleMutationResponse> archiveSkill(@PathVariable String namespace,
                                                                    @PathVariable String slug,
                                                                    @RequestBody(required = false) AdminSkillActionRequest request,
                                                                    @RequestAttribute("userId") String userId,
                                                                    @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
                                                                    HttpServletRequest httpRequest) {
        Skill skill = findSkill(namespace, slug);
        Skill archived = skillGovernanceService.archiveSkill(
                skill.getId(),
                userId,
                userNsRoles != null ? userNsRoles : Map.of(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent"),
                request != null ? request.reason() : null
        );

        return ok("response.success.updated",
                new SkillLifecycleMutationResponse(archived.getId(), null, "ARCHIVE", archived.getStatus().name()));
    }

    @PostMapping("/{namespace}/{slug}/unarchive")
    public ApiResponse<SkillLifecycleMutationResponse> unarchiveSkill(@PathVariable String namespace,
                                                                      @PathVariable String slug,
                                                                      @RequestAttribute("userId") String userId,
                                                                      @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
                                                                      HttpServletRequest httpRequest) {
        Skill skill = findSkill(namespace, slug);
        Skill restored = skillGovernanceService.unarchiveSkill(
                skill.getId(),
                userId,
                userNsRoles != null ? userNsRoles : Map.of(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );

        return ok("response.success.updated",
                new SkillLifecycleMutationResponse(restored.getId(), null, "UNARCHIVE", restored.getStatus().name()));
    }

    @DeleteMapping("/{namespace}/{slug}/versions/{version}")
    public ApiResponse<SkillLifecycleMutationResponse> deleteVersion(@PathVariable String namespace,
                                                                     @PathVariable String slug,
                                                                     @PathVariable String version,
                                                                     @RequestAttribute("userId") String userId,
                                                                     @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
                                                                     HttpServletRequest httpRequest) {
        Skill skill = findSkill(namespace, slug);
        SkillVersion skillVersion = skillVersionRepository.findBySkillIdAndVersion(skill.getId(), version)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.version.notFound", version));
        skillGovernanceService.deleteVersion(
                skill,
                skillVersion,
                userId,
                userNsRoles != null ? userNsRoles : Map.of(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );

        return ok("response.success.deleted",
                new SkillLifecycleMutationResponse(skill.getId(), skillVersion.getId(), "DELETE_VERSION", version));
    }

    @PostMapping("/{namespace}/{slug}/versions/{version}/withdraw-review")
    public ApiResponse<SkillLifecycleMutationResponse> withdrawReview(@PathVariable String namespace,
                                                                     @PathVariable String slug,
                                                                     @PathVariable String version,
                                                                     @RequestAttribute("userId") String userId,
                                                                     HttpServletRequest httpRequest) {
        Skill skill = findSkill(namespace, slug);
        SkillVersion skillVersion = skillVersionRepository.findBySkillIdAndVersion(skill.getId(), version)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.version.notFound", version));
        reviewService.withdrawReview(skillVersion.getId(), userId);
        auditLogService.record(
                userId,
                "REVIEW_WITHDRAW",
                "SKILL_VERSION",
                skillVersion.getId(),
                null,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent"),
                "{\"version\":\"" + version.replace("\"", "\\\"") + "\"}"
        );

        return ok("response.success.updated",
                new SkillLifecycleMutationResponse(skill.getId(), skillVersion.getId(), "WITHDRAW_REVIEW", "DELETED"));
    }

    private Skill findSkill(String namespaceSlug, String skillSlug) {
        String cleanNamespace = namespaceSlug.startsWith("@") ? namespaceSlug.substring(1) : namespaceSlug;
        Namespace namespace = namespaceRepository.findBySlug(cleanNamespace)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.slug.notFound", cleanNamespace));
        return skillRepository.findByNamespaceIdAndSlug(namespace.getId(), skillSlug)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.notFound", skillSlug));
    }
}
