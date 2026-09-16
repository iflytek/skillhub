package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.MySkillSuiteSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.service.SkillSuiteAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Current-user dashboard transport for manageable Suite drafts and published versions. */
@RestController
@Tag(name = "My Skill Suites")
@RequestMapping({"/api/v1/me/suites", "/api/web/me/suites"})
public class MySkillSuiteController extends BaseApiController {

    private final SkillSuiteAppService appService;

    public MySkillSuiteController(SkillSuiteAppService appService, ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.appService = appService;
    }

    @GetMapping
    @Operation(operationId = "listMySkillSuites", summary = "List Suite versions manageable by the current user")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Manageable Suite page returned")
    public ApiResponse<PageResponse<MySkillSuiteSummaryResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles
    ) {
        return ok("response.success.read", appService.listMine(
                userId, roles == null ? Map.of() : roles, q, page, size));
    }
}
