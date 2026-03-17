package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.ratelimit.RateLimit;
import com.iflytek.skillhub.service.SkillSearchAppService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/api/web/skills"})
public class SkillSearchController extends BaseApiController {

    private final SkillSearchAppService skillSearchAppService;

    public SkillSearchController(SkillSearchAppService skillSearchAppService,
                                 ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.skillSearchAppService = skillSearchAppService;
    }

    @GetMapping
    @RateLimit(category = "search", authenticated = 60, anonymous = 20)
    public ApiResponse<SkillSearchAppService.SearchResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String namespace,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        SkillSearchAppService.SearchResponse response = skillSearchAppService.search(
                q,
                namespace,
                sort,
                page,
                size,
                userId,
                userNsRoles
        );

        return ok("response.success.read", response);
    }
}
