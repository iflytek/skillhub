package com.iflytek.skillhub.compat;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.compat.dto.ClawHubDeleteResponse;
import com.iflytek.skillhub.compat.dto.ClawHubPublishResponse;
import com.iflytek.skillhub.compat.dto.ClawHubResolveResponse;
import com.iflytek.skillhub.compat.dto.ClawHubSearchResponse;
import com.iflytek.skillhub.compat.dto.ClawHubSkillListResponse;
import com.iflytek.skillhub.compat.dto.ClawHubSkillResponse;
import com.iflytek.skillhub.compat.dto.ClawHubStarResponse;
import com.iflytek.skillhub.compat.dto.ClawHubUnstarResponse;
import com.iflytek.skillhub.compat.dto.ClawHubWhoamiResponse;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.ratelimit.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Compatibility controller that exposes SkillHub content using ClawHub-style
 * routes and payload shapes expected by legacy clients.
 */
@RestController
@RequestMapping("/api/v1")
public class ClawHubCompatController {

    private final ClawHubCompatAppService clawHubCompatAppService;

    public ClawHubCompatController(ClawHubCompatAppService clawHubCompatAppService) {
        this.clawHubCompatAppService = clawHubCompatAppService;
    }

    @RateLimit(category = "search", authenticated = 60, anonymous = 20)
    @GetMapping("/search")
    public ClawHubSearchResponse search(@RequestParam String q,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int limit,
                                        @RequestAttribute(value = "userId", required = false) String userId,
                                        @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        return clawHubCompatAppService.search(q, page, limit, userId, userNsRoles);
    }

    @RateLimit(category = "resolve", authenticated = 60, anonymous = 20)
    @GetMapping("/resolve")
    public ClawHubResolveResponse resolveByQuery(@RequestParam String slug,
                                                 @RequestParam(required = false) String version,
                                                 @RequestParam(required = false) String hash,
                                                 @RequestAttribute(value = "userId", required = false) String userId,
                                                 @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        return clawHubCompatAppService.resolveByQuery(slug, version, hash, userId, userNsRoles);
    }

    @RateLimit(category = "resolve", authenticated = 60, anonymous = 20)
    @GetMapping("/resolve/{canonicalSlug}")
    public ClawHubResolveResponse resolve(@PathVariable String canonicalSlug,
                                          @RequestParam(defaultValue = "latest") String version,
                                          @RequestAttribute(value = "userId", required = false) String userId,
                                          @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        return clawHubCompatAppService.resolve(canonicalSlug, version, userId, userNsRoles);
    }

    @RateLimit(category = "download", authenticated = 60, anonymous = 20)
    @GetMapping("/download/{canonicalSlug}")
    public ResponseEntity<Void> downloadByPath(@PathVariable String canonicalSlug,
                                               @RequestParam(defaultValue = "latest") String version) {
        return redirect(clawHubCompatAppService.downloadLocationByPath(canonicalSlug, version));
    }

    @RateLimit(category = "download", authenticated = 60, anonymous = 20)
    @GetMapping("/download")
    public ResponseEntity<Void> downloadByQuery(@RequestParam String slug,
                                                @RequestParam(defaultValue = "latest") String version) {
        return redirect(clawHubCompatAppService.downloadLocationByQuery(slug, version));
    }

    @RateLimit(category = "skills", authenticated = 60, anonymous = 20)
    @GetMapping("/skills")
    public ClawHubSkillListResponse listSkills(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "25") int limit,
                                               @RequestParam(required = false) String sort,
                                               @RequestAttribute(value = "userId", required = false) String userId,
                                               @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        return clawHubCompatAppService.listSkills(page, limit, sort, userId, userNsRoles);
    }

    @RateLimit(category = "skills", authenticated = 60, anonymous = 20)
    @GetMapping("/skills/{canonicalSlug}")
    public ClawHubSkillResponse getSkill(@PathVariable String canonicalSlug,
                                         @RequestAttribute(value = "userId", required = false) String userId) {
        return clawHubCompatAppService.getSkill(canonicalSlug, userId);
    }

    @RateLimit(category = "skills", authenticated = 60, anonymous = 20)
    @DeleteMapping("/skills/{canonicalSlug}")
    public ClawHubDeleteResponse deleteSkill(@PathVariable String canonicalSlug,
                                             @AuthenticationPrincipal PlatformPrincipal principal) {
        return clawHubCompatAppService.deleteSkill();
    }

    @RateLimit(category = "skills", authenticated = 60, anonymous = 20)
    @PostMapping("/skills/{canonicalSlug}/undelete")
    public ClawHubDeleteResponse undeleteSkill(@PathVariable String canonicalSlug,
                                               @AuthenticationPrincipal PlatformPrincipal principal) {
        return clawHubCompatAppService.undeleteSkill();
    }

    @RateLimit(category = "stars", authenticated = 60, anonymous = 20)
    @PostMapping("/stars/{canonicalSlug}")
    public ClawHubStarResponse starSkill(@PathVariable String canonicalSlug,
                                         @AuthenticationPrincipal PlatformPrincipal principal) {
        return clawHubCompatAppService.starSkill(canonicalSlug, principal);
    }

    @RateLimit(category = "stars", authenticated = 60, anonymous = 20)
    @DeleteMapping("/stars/{canonicalSlug}")
    public ClawHubUnstarResponse unstarSkill(@PathVariable String canonicalSlug,
                                             @AuthenticationPrincipal PlatformPrincipal principal) {
        return clawHubCompatAppService.unstarSkill(canonicalSlug, principal);
    }

    @RateLimit(category = "skills", authenticated = 60, anonymous = 20)
    @PostMapping("/skills")
    public ClawHubPublishResponse publishSkill(@RequestParam("payload") String payloadJson,
                                               @RequestParam("files") MultipartFile[] files,
                                               @RequestParam(value = "confirmWarnings", defaultValue = "false") boolean confirmWarnings,
                                               @AuthenticationPrincipal PlatformPrincipal principal,
                                               HttpServletRequest request) throws IOException {
        return clawHubCompatAppService.publishSkill(
                payloadJson,
                files,
                confirmWarnings,
                principal,
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
        );
    }

    @RateLimit(category = "publish", authenticated = 60, anonymous = 20)
    @PostMapping("/publish")
    public ClawHubPublishResponse publish(@RequestParam("file") MultipartFile file,
                                          @RequestParam("namespace") String namespace,
                                          @RequestParam(value = "confirmWarnings", defaultValue = "false") boolean confirmWarnings,
                                          @AuthenticationPrincipal PlatformPrincipal principal,
                                          HttpServletRequest request) throws IOException {
        return clawHubCompatAppService.publish(
                file,
                namespace,
                confirmWarnings,
                principal,
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
        );
    }

    @RateLimit(category = "whoami", authenticated = 60, anonymous = 20)
    @GetMapping("/whoami")
    public ClawHubWhoamiResponse whoami(@AuthenticationPrincipal PlatformPrincipal principal) {
        return clawHubCompatAppService.whoami(principal);
    }

    private ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location)
                .build();
    }
}
