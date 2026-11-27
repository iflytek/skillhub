package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.service.SkillSlugResolutionService.Preference;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.SkillAvatarDecisionRequest;
import com.iflytek.skillhub.dto.SkillAvatarReasonRequest;
import com.iflytek.skillhub.dto.SkillAvatarStatusResponse;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.SkillAvatarAppService;
import com.iflytek.skillhub.service.SkillAvatarUploadService;
import com.iflytek.skillhub.dto.SkillAvatarUploadResponse;
import com.iflytek.skillhub.dto.SkillAvatarSubmitRequest;
import com.iflytek.skillhub.ratelimit.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/api/v1/skills", "/api/web/skills"})
public class SkillAvatarController extends BaseApiController {
    private final SkillAvatarAppService service;
    private final SkillAvatarUploadService uploads;

    public SkillAvatarController(SkillAvatarAppService service, SkillAvatarUploadService uploads, ApiResponseFactory responses) {
        super(responses);
        this.service = service;
        this.uploads = uploads;
    }

    @PostMapping(value = "/avatar-uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RateLimit(category = "publish", authenticated = 10, anonymous = 0)
    public ApiResponse<SkillAvatarUploadResponse> upload(@RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal PlatformPrincipal principal) throws IOException {
        if (file.getSize() < 1 || file.getSize() > com.iflytek.skillhub.service.SkillAvatarImageProcessor.MAX_BYTES) {
            throw new DomainBadRequestException("error.skill.avatar.invalidImage");
        }
        return ok("response.success.created", uploads.upload(userId(principal), file.getBytes()));
    }

    @GetMapping("/avatar-uploads/{uploadId}/image")
    public ResponseEntity<InputStreamResource> uploadPreview(@PathVariable String uploadId,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return png(uploads.preview(uploadId, userId(principal)));
    }

    @PostMapping(value = "/{namespace}/{slug}/avatar", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<SkillAvatarStatusResponse> submitUpload(@PathVariable String namespace, @PathVariable String slug,
            @Valid @RequestBody SkillAvatarSubmitRequest input, @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest request) {
        return ok("response.success.created", uploads.submit(namespace, slug, userId(principal), input.uploadId(),
                AuditRequestContext.from(request)));
    }

    @GetMapping("/{namespace}/{slug}/avatar")
    public ResponseEntity<InputStreamResource> current(@PathVariable String namespace, @PathVariable String slug,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles) {
        InputStream image = principal != null && principal.tokenCapabilities() != null
                ? service.readActive(namespace, slug, userId(principal), roles, platformRoles(principal), Preference.PUBLIC_PUBLISHED)
                : service.readActive(namespace, slug, userId(principal), roles, platformRoles(principal));
        return png(image);
    }

    @PostMapping(value = "/{namespace}/{slug}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SkillAvatarStatusResponse> submit(@PathVariable String namespace, @PathVariable String slug,
            @RequestParam("file") MultipartFile file, @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) throws IOException {
        if (file.getSize() < 1 || file.getSize() > com.iflytek.skillhub.service.SkillAvatarImageProcessor.MAX_BYTES) {
            throw new DomainBadRequestException("error.skill.avatar.invalidImage");
        }
        AuditRequestContext context = AuditRequestContext.from(httpRequest);
        SkillAvatarStatusResponse created = service.submit(namespace, slug, userId(principal), file.getBytes(), context);
        if ("PENDING".equals(created.machineStatus()) && "PENDING".equals(created.candidateStatus())) {
            return ok("response.success.created", service.processMachine(created.candidateId(), context));
        }
        return ok("response.success.created", created);
    }

    @GetMapping("/{namespace}/{slug}/avatar/status")
    public ApiResponse<SkillAvatarStatusResponse> status(@PathVariable String namespace, @PathVariable String slug,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.read", service.ownerStatus(namespace, slug, userId(principal)));
    }

    @GetMapping("/{namespace}/{slug}/avatar/candidates/{candidateId}/image")
    public ResponseEntity<InputStreamResource> candidate(@PathVariable String namespace, @PathVariable String slug,
            @PathVariable long candidateId, @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles) {
        return png(service.readCandidate(namespace, slug, candidateId, userId(principal), roles, platformRoles(principal)));
    }

    @PostMapping("/{namespace}/{slug}/avatar/candidates/{candidateId}/appeal")
    public ApiResponse<SkillAvatarStatusResponse> appeal(@PathVariable String namespace, @PathVariable String slug,
            @PathVariable long candidateId, @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest request) {
        return ok("response.success.updated", service.appeal(namespace, slug, candidateId,
                userId(principal), AuditRequestContext.from(request)));
    }

    @PostMapping("/{namespace}/{slug}/avatar/candidates/{candidateId}/retry")
    public ApiResponse<SkillAvatarStatusResponse> retry(@PathVariable String namespace, @PathVariable String slug,
            @PathVariable long candidateId, @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest request) {
        service.requireCurrentOwnerCandidate(namespace, slug, candidateId, userId(principal));
        return ok("response.success.updated", service.processMachine(candidateId, AuditRequestContext.from(request)));
    }

    @Deprecated
    public ApiResponse<List<SkillAvatarStatusResponse>> queue(
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles) {
        return ok("response.success.read", service.reviewQueue(userId(principal), roles, platformRoles(principal)));
    }

    @Deprecated
    public ApiResponse<SkillAvatarStatusResponse> decide(@PathVariable String namespace, @PathVariable String slug,
            @PathVariable long candidateId, @Valid @RequestBody SkillAvatarDecisionRequest decision,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            HttpServletRequest request) {
        return ok("response.success.updated", service.decide(namespace, slug, candidateId,
                decision.approved(), decision.reason(), userId(principal), roles, platformRoles(principal),
                AuditRequestContext.from(request)));
    }

    @DeleteMapping("/{namespace}/{slug}/avatar")
    public ApiResponse<Void> delete(@PathVariable String namespace, @PathVariable String slug,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            HttpServletRequest request) {
        service.remove(namespace, slug, userId(principal), false, null, roles, platformRoles(principal),
                AuditRequestContext.from(request));
        return ok("response.success.updated", null);
    }

    @PostMapping("/{namespace}/{slug}/avatar/takedown")
    public ApiResponse<Void> takedown(@PathVariable String namespace, @PathVariable String slug,
            @Valid @RequestBody SkillAvatarReasonRequest input, @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            HttpServletRequest request) {
        service.remove(namespace, slug, userId(principal), true, input.reason(), roles, platformRoles(principal),
                AuditRequestContext.from(request));
        return ok("response.success.updated", null);
    }

    private ResponseEntity<InputStreamResource> png(InputStream input) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
                .body(new InputStreamResource(input));
    }

    private String userId(PlatformPrincipal principal) {
        return principal == null ? null : principal.userId();
    }

    private Set<String> platformRoles(PlatformPrincipal principal) {
        return principal == null || principal.platformRoles() == null ? Set.of() : principal.platformRoles();
    }
}
