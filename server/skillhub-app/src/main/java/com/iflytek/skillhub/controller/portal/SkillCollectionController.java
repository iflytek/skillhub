package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.MessageResponse;
import com.iflytek.skillhub.dto.collection.AddContributorRequest;
import com.iflytek.skillhub.dto.collection.AddSkillToCollectionRequest;
import com.iflytek.skillhub.dto.collection.CollectionSkillReorderRequest;
import com.iflytek.skillhub.dto.collection.SkillCollectionContributorResponse;
import com.iflytek.skillhub.dto.collection.SkillCollectionCreateRequest;
import com.iflytek.skillhub.dto.collection.SkillCollectionMemberResponse;
import com.iflytek.skillhub.dto.collection.SkillCollectionResponse;
import com.iflytek.skillhub.dto.collection.SkillCollectionUpdateRequest;
import com.iflytek.skillhub.service.SkillCollectionPortalCommandAppService;
import com.iflytek.skillhub.service.SkillCollectionPortalQueryAppService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/web")
public class SkillCollectionController extends BaseApiController {
    private final SkillCollectionPortalQueryAppService queryService;
    private final SkillCollectionPortalCommandAppService commandService;

    public SkillCollectionController(
            SkillCollectionPortalQueryAppService queryService,
            SkillCollectionPortalCommandAppService commandService,
            ApiResponseFactory responseFactory
    ) {
        super(responseFactory);
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @GetMapping("/me/collections")
    public ApiResponse<Page<SkillCollectionResponse>> listMine(
            Pageable pageable,
            @RequestAttribute("userId") String userId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.read", queryService.listMine(pageable, userId, isAdminEquivalent(principal)));
    }

    @PostMapping("/collections")
    public ApiResponse<SkillCollectionResponse> create(
            @Valid @RequestBody SkillCollectionCreateRequest request,
            @RequestAttribute("userId") String userId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.created", commandService.create(
                userId,
                isAdminEquivalent(principal),
                null,
                request
        ));
    }

    @GetMapping("/collections/{id}")
    public ApiResponse<SkillCollectionResponse> getForActor(
            @PathVariable Long id,
            @RequestAttribute("userId") String userId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.read", queryService.getForActor(id, userId, isAdminEquivalent(principal), false));
    }

    @PatchMapping("/collections/{id}")
    public ApiResponse<SkillCollectionResponse> updateMetadata(
            @PathVariable Long id,
            @Valid @RequestBody SkillCollectionUpdateRequest request,
            @RequestAttribute("userId") String userId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.updated", commandService.updateMetadata(id, userId, isAdminEquivalent(principal), request));
    }

    @PatchMapping("/collections/{id}/visibility")
    public ApiResponse<SkillCollectionResponse> setVisibility(
            @PathVariable Long id,
            @Valid @RequestBody VisibilityBody request,
            @RequestAttribute("userId") String userId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        SkillVisibility visibility = SkillVisibility.valueOf(request.visibility());
        return ok("response.success.updated", commandService.setVisibility(id, userId, isAdminEquivalent(principal), visibility));
    }

    @DeleteMapping("/collections/{id}")
    public ApiResponse<MessageResponse> delete(
            @PathVariable Long id,
            @RequestAttribute("userId") String userId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        commandService.delete(id, userId, isAdminEquivalent(principal));
        return ok("response.success.deleted", new MessageResponse("Collection deleted successfully"));
    }

    @PostMapping("/collections/{id}/skills")
    public ApiResponse<SkillCollectionMemberResponse> addSkill(
            @PathVariable Long id,
            @Valid @RequestBody AddSkillToCollectionRequest request,
            @RequestAttribute("userId") String userId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.created",
                commandService.addSkill(id, userId, isAdminEquivalent(principal), request.skillId()));
    }

    @DeleteMapping("/collections/{id}/skills/{skillId}")
    public ApiResponse<MessageResponse> removeSkill(
            @PathVariable Long id,
            @PathVariable Long skillId,
            @RequestAttribute("userId") String userId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        commandService.removeSkill(id, userId, isAdminEquivalent(principal), skillId);
        return ok("response.success.deleted", new MessageResponse("Skill removed successfully"));
    }

    @PutMapping("/collections/{id}/skills/order")
    public ApiResponse<List<SkillCollectionMemberResponse>> reorderSkills(
            @PathVariable Long id,
            @Valid @RequestBody CollectionSkillReorderRequest request,
            @RequestAttribute("userId") String userId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.updated",
                commandService.reorder(id, userId, isAdminEquivalent(principal), request.skillIdsInOrder()));
    }

    @GetMapping("/collections/{id}/contributors")
    public ApiResponse<List<SkillCollectionContributorResponse>> listContributors(
            @PathVariable Long id,
            @RequestAttribute("userId") String userId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.read", commandService.listContributors(id, userId, isAdminEquivalent(principal)));
    }

    @PostMapping("/collections/{id}/contributors")
    public ApiResponse<SkillCollectionContributorResponse> addContributor(
            @PathVariable Long id,
            @Valid @RequestBody AddContributorRequest request,
            @RequestAttribute("userId") String userId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.created",
                commandService.addContributor(id, userId, isAdminEquivalent(principal), request.userId()));
    }

    @DeleteMapping("/collections/{id}/contributors/{userId}")
    public ApiResponse<MessageResponse> removeContributor(
            @PathVariable Long id,
            @PathVariable("userId") String contributorUserId,
            @RequestAttribute("userId") String userId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        commandService.removeContributor(id, userId, isAdminEquivalent(principal), contributorUserId);
        return ok("response.success.deleted", new MessageResponse("Contributor removed successfully"));
    }

    private boolean isAdminEquivalent(PlatformPrincipal principal) {
        return principal != null
                && principal.platformRoles() != null
                && (principal.platformRoles().contains("SKILL_ADMIN")
                || principal.platformRoles().contains("SUPER_ADMIN"));
    }

    public record VisibilityBody(
            @NotBlank(message = "{validation.skillCollection.visibility.notBlank}")
            @Pattern(regexp = "PUBLIC|PRIVATE", message = "{validation.skillCollection.visibility.allowed}")
            String visibility
    ) {
    }
}
