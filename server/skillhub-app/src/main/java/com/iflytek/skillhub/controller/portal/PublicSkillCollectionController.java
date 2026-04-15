package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.collection.SkillCollectionResponse;
import com.iflytek.skillhub.service.SkillCollectionPortalQueryAppService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/web/public")
public class PublicSkillCollectionController extends BaseApiController {
    private final SkillCollectionPortalQueryAppService queryService;

    public PublicSkillCollectionController(
            SkillCollectionPortalQueryAppService queryService,
            ApiResponseFactory responseFactory
    ) {
        super(responseFactory);
        this.queryService = queryService;
    }

    @GetMapping("/collections/{ownerId}/{slug}")
    public ApiResponse<SkillCollectionResponse> getPublicCollection(
            @PathVariable String ownerId,
            @PathVariable String slug
    ) {
        return ok("response.success.read", queryService.getPublicByOwnerAndSlug(ownerId, slug, null));
    }
}
