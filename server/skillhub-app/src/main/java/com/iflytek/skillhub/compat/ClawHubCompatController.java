package com.iflytek.skillhub.compat;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.compat.dto.ClawHubResolveResponse;
import com.iflytek.skillhub.compat.dto.ClawHubSearchResponse;
import com.iflytek.skillhub.compat.dto.ClawHubWhoamiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/compat/v1")
public class ClawHubCompatController {

    private final CanonicalSlugMapper mapper;

    public ClawHubCompatController(CanonicalSlugMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping("/search")
    public ClawHubSearchResponse search(@RequestParam String q) {
        // Return empty results for now (placeholder)
        return new ClawHubSearchResponse(List.of());
    }

    @GetMapping("/resolve/{canonicalSlug}")
    public ClawHubResolveResponse resolve(
            @PathVariable String canonicalSlug,
            @RequestParam(defaultValue = "latest") String version) {
        SkillCoordinate coord = mapper.fromCanonical(canonicalSlug);
        return new ClawHubResolveResponse(
                canonicalSlug,
                version,
                "/api/v1/skills/" + coord.namespace() + "/" + coord.slug() + "/download"
        );
    }

    @GetMapping("/whoami")
    public ClawHubWhoamiResponse whoami(@AuthenticationPrincipal PlatformPrincipal principal) {
        return new ClawHubWhoamiResponse(
                principal.userId(),
                principal.displayName(),
                principal.email()
        );
    }
}
