package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.ResourceSearchResponse;
import com.iflytek.skillhub.ratelimit.RateLimit;
import com.iflytek.skillhub.service.ResourceDiscoveryAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Type-explicit discovery endpoint for clients that understand both Skills and Suites. */
@RestController
@Tag(name = "Resource discovery")
@RequestMapping({"/api/v1/resources", "/api/web/resources"})
public class ResourceDiscoveryController extends BaseApiController {

    private final ResourceDiscoveryAppService appService;

    public ResourceDiscoveryController(
            ResourceDiscoveryAppService appService,
            ApiResponseFactory responseFactory
    ) {
        super(responseFactory);
        this.appService = appService;
    }

    @GetMapping
    @Operation(operationId = "searchResources", summary = "Search Skills and Suites with explicit resource types")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Resource page returned")
    @RateLimit(category = "search", authenticated = 60, anonymous = 20)
    public ApiResponse<ResourceSearchResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String resourceType,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles
    ) {
        return ok("response.success.read", appService.search(
                q, namespace, resourceType, sort, page, size,
                roles == null ? java.util.Set.of() : roles.keySet()));
    }
}
