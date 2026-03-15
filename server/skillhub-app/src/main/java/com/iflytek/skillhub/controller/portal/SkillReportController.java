package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.report.SkillReportService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.SkillReportMutationResponse;
import com.iflytek.skillhub.dto.SkillReportSubmitRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/skills", "/api/web/skills"})
public class SkillReportController extends BaseApiController {

    private final NamespaceRepository namespaceRepository;
    private final SkillRepository skillRepository;
    private final SkillReportService skillReportService;

    public SkillReportController(NamespaceRepository namespaceRepository,
                                 SkillRepository skillRepository,
                                 SkillReportService skillReportService,
                                 ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.namespaceRepository = namespaceRepository;
        this.skillRepository = skillRepository;
        this.skillReportService = skillReportService;
    }

    @PostMapping("/{namespace}/{slug}/reports")
    public ApiResponse<SkillReportMutationResponse> submitReport(@PathVariable String namespace,
                                                                 @PathVariable String slug,
                                                                 @RequestBody SkillReportSubmitRequest request,
                                                                 @RequestAttribute("userId") String userId,
                                                                 HttpServletRequest httpRequest) {
        Skill skill = findSkill(namespace, slug);
        var report = skillReportService.submitReport(
                skill.getId(),
                userId,
                request.reason(),
                request.details(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        return ok("response.success.created", new SkillReportMutationResponse(report.getId(), report.getStatus().name()));
    }

    private Skill findSkill(String namespaceSlug, String skillSlug) {
        String cleanNamespace = namespaceSlug.startsWith("@") ? namespaceSlug.substring(1) : namespaceSlug;
        Namespace namespace = namespaceRepository.findBySlug(cleanNamespace)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.slug.notFound", cleanNamespace));
        return skillRepository.findByNamespaceIdAndSlug(namespace.getId(), skillSlug)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.notFound", skillSlug));
    }
}
