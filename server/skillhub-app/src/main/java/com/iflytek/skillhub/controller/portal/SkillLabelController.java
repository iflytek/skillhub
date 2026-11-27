package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.service.SkillSlugResolutionService.Preference;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.MessageResponse;
import com.iflytek.skillhub.dto.SkillLabelDto;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.SkillLabelAppService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({
        "/api/v1/skills/{namespace}/{slug}/labels",
        "/api/web/skills/{namespace}/{slug}/labels"
})
public class SkillLabelController extends BaseApiController {

    private final SkillLabelAppService skillLabelAppService;

    public SkillLabelController(SkillLabelAppService skillLabelAppService,
                                ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.skillLabelAppService = skillLabelAppService;
    }

    @GetMapping
    public ApiResponse<List<SkillLabelDto>> listLabels(@PathVariable String namespace,
                                                       @PathVariable String slug,
                                                       @RequestAttribute(value = "userId", required = false) String userId,
                                                       @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
                                                       @RequestAttribute(value = "apiToken", required = false) Boolean apiToken) {
        Map<Long, NamespaceRole> roles = userNsRoles != null ? userNsRoles : Map.of();
        List<SkillLabelDto> labels = Boolean.TRUE.equals(apiToken)
                ? skillLabelAppService.listSkillLabels(namespace, slug, userId, roles, Preference.PUBLIC_PUBLISHED)
                : skillLabelAppService.listSkillLabels(namespace, slug, userId, roles);
        return ok("response.success.read", labels);
    }

    @PutMapping("/{labelSlug}")
    public ApiResponse<SkillLabelDto> attachLabel(@PathVariable String namespace,
                                                  @PathVariable String slug,
                                                  @PathVariable String labelSlug,
                                                  @RequestAttribute("userId") String userId,
                                                  @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
                                                  HttpServletRequest httpRequest) {
        return ok("response.success.updated",
                skillLabelAppService.attachLabel(
                        namespace,
                        slug,
                        labelSlug,
                        userId,
                        userNsRoles != null ? userNsRoles : Map.of(),
                        AuditRequestContext.from(httpRequest)
                ));
    }

    @DeleteMapping("/{labelSlug}")
    public ApiResponse<MessageResponse> detachLabel(@PathVariable String namespace,
                                                    @PathVariable String slug,
                                                    @PathVariable String labelSlug,
                                                    @RequestAttribute("userId") String userId,
                                                    @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
                                                    HttpServletRequest httpRequest) {
        return ok("response.success.deleted",
                skillLabelAppService.detachLabel(
                        namespace,
                        slug,
                        labelSlug,
                        userId,
                        userNsRoles != null ? userNsRoles : Map.of(),
                        AuditRequestContext.from(httpRequest)
                ));
    }
}
