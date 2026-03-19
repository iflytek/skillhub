package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.SkillTag;
import com.iflytek.skillhub.domain.skill.service.SkillTagService;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.MessageResponse;
import com.iflytek.skillhub.dto.TagRequest;
import com.iflytek.skillhub.dto.TagResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Endpoints for reading and mutating named tags that point to skill versions.
 */
@RestController
@RequestMapping({
        "/api/v1/skills/{namespace}/{slug}/tags",
        "/api/web/skills/{namespace}/{slug}/tags"
})
public class SkillTagController extends BaseApiController {

    private final SkillTagService skillTagService;

    public SkillTagController(SkillTagService skillTagService,
                              ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.skillTagService = skillTagService;
    }

    @GetMapping
    public ApiResponse<List<TagResponse>> listTags(
            @PathVariable String namespace,
            @PathVariable String slug,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        List<SkillTag> tags = skillTagService.listTags(
                namespace,
                slug,
                userId,
                userNsRoles != null ? userNsRoles : Map.of()
        );

        List<TagResponse> response = tags.stream()
                .map(TagResponse::from)
                .collect(Collectors.toList());

        return ok("response.success.read", response);
    }

    @PutMapping("/{tagName}")
    public ApiResponse<TagResponse> createOrMoveTag(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String tagName,
            @Valid @RequestBody TagRequest request,
            @RequestAttribute("userId") String userId) {

        SkillTag tag = skillTagService.createOrMoveTag(
                namespace,
                slug,
                tagName,
                request.targetVersion(),
                userId
        );

        return ok("response.success.updated", TagResponse.from(tag));
    }

    @DeleteMapping("/{tagName}")
    public ApiResponse<MessageResponse> deleteTag(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String tagName,
            @RequestAttribute("userId") String userId) {

        skillTagService.deleteTag(namespace, slug, tagName, userId);

        return ok("response.success.deleted", new MessageResponse("Tag deleted"));
    }
}
