package com.iflytek.skillhub.controller.cli;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.cli.CliNamespaceSyncResponse;
import com.iflytek.skillhub.ratelimit.RateLimit;
import com.iflytek.skillhub.service.cli.CliSkillAppService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * CLI namespace-scoped read endpoints.
 */
@RestController
@RequestMapping("/api/cli/v1/namespaces")
public class CliNamespaceController extends BaseApiController {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 100;

    private final CliSkillAppService cliSkillAppService;

    public CliNamespaceController(CliSkillAppService cliSkillAppService, ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.cliSkillAppService = cliSkillAppService;
    }

    @GetMapping("/{namespace}/skills")
    @RateLimit(category = "skills", authenticated = 60, anonymous = 0)
    public ApiResponse<CliNamespaceSyncResponse> listSkills(
            @PathVariable String namespace,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "100") int limit,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        int page = parseCursor(cursor);
        int normalizedLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return ok("response.success.read", cliSkillAppService.listNamespaceSkills(
                namespace,
                page,
                normalizedLimit,
                userId,
                userNsRoles
        ));
    }

    private int parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            int page = Integer.parseInt(cursor);
            if (page < 0) {
                throw new NumberFormatException("negative cursor");
            }
            return page;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("cursor must be a non-negative page number", ex);
        }
    }
}
