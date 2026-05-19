package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.ratelimit.RateLimit;
import com.iflytek.skillhub.service.SkillSearchAppService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Portal search endpoint that adapts HTTP query parameters to the search
 * application service and visibility scope.
 */
@RestController
@RequestMapping({"/api/web/skills"})
public class SkillSearchController extends BaseApiController {
    private static final Pattern NON_NEGATIVE_INTEGER = Pattern.compile("\\d+");
    private static final String DEFAULT_SORT = "newest";
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

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
            @RequestParam(name = "label", required = false) java.util.List<String> labels,
            @Parameter(schema = @Schema(defaultValue = DEFAULT_SORT))
            @RequestParam(required = false) String sort,
            @Parameter(schema = @Schema(type = "integer", defaultValue = "0", minimum = "0"))
            @RequestParam(required = false) String page,
            @Parameter(schema = @Schema(type = "integer", defaultValue = "20", minimum = "1"))
            @RequestParam(required = false) String size,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        SkillSearchAppService.SearchResponse response = skillSearchAppService.search(
                q,
                namespace,
                normalizeSort(sort),
                parseNonNegativeInt(page, DEFAULT_PAGE),
                parsePositiveInt(size, DEFAULT_SIZE),
                labels,
                userId,
                userNsRoles
        );

        return ok("response.success.read", response);
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return DEFAULT_SORT;
        }
        return sort.trim();
    }

    private int parseNonNegativeInt(String rawValue, int defaultValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        String normalized = rawValue.trim();
        if (!NON_NEGATIVE_INTEGER.matcher(normalized).matches()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private int parsePositiveInt(String rawValue, int defaultValue) {
        int parsed = parseNonNegativeInt(rawValue, defaultValue);
        return parsed > 0 ? parsed : defaultValue;
    }
}
