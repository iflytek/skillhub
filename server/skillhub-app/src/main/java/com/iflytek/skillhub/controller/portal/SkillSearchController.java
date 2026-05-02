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

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Portal search endpoint that adapts HTTP query parameters to the search
 * application service and visibility scope.
 */
@RestController
@RequestMapping({"/api/web/skills"})
public class SkillSearchController extends BaseApiController {
    private static final Pattern NON_NEGATIVE_INTEGER = Pattern.compile("\\d+");
    private static final Set<String> ALLOWED_SORTS = Set.of("newest", "downloads", "rating", "relevance");
    private static final String DEFAULT_SORT = "newest";
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int MAX_PAGE = 10_000;

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
            @Parameter(schema = @Schema(type = "integer", defaultValue = "0", minimum = "0", maximum = "" + MAX_PAGE))
            @RequestParam(required = false) String page,
            @Parameter(schema = @Schema(type = "integer", defaultValue = "20", minimum = "1", maximum = "" + MAX_SIZE))
            @RequestParam(required = false) String size,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        SkillSearchAppService.SearchResponse response = skillSearchAppService.search(
                q,
                namespace,
                normalizeSort(sort),
                parseNonNegativeInt(page, DEFAULT_PAGE, MAX_PAGE),
                parsePositiveInt(size, DEFAULT_SIZE, MAX_SIZE),
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
        String normalized = sort.trim().toLowerCase(Locale.ROOT);
        return ALLOWED_SORTS.contains(normalized) ? normalized : DEFAULT_SORT;
    }

    private int parseNonNegativeInt(String rawValue, int defaultValue, int maxValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        String normalized = rawValue.trim();
        if (!NON_NEGATIVE_INTEGER.matcher(normalized).matches()) {
            return defaultValue;
        }
        try {
            return Math.min(Integer.parseInt(normalized), maxValue);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private int parsePositiveInt(String rawValue, int defaultValue, int maxValue) {
        int parsed = parseNonNegativeInt(rawValue, defaultValue, maxValue);
        return parsed > 0 ? parsed : defaultValue;
    }
}
