package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.dto.MemberResponse;
import com.iflytek.skillhub.service.NamespacePortalCommandAppService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

/** Session-only namespace governance endpoints; API tokens have no route policy for these writes. */
@RestController
@RequestMapping("/api/web/namespaces/{slug}")
public class TrustedPublishPolicyController {
    private final NamespaceRepository namespaces;
    private final NamespacePortalCommandAppService commands;

    public TrustedPublishPolicyController(NamespaceRepository namespaces, NamespacePortalCommandAppService commands) {
        this.namespaces = namespaces;
        this.commands = commands;
    }

    public record ToggleRequest(@NotNull Boolean enabled) {}
    public record PolicyResponse(boolean enabled) {}

    @GetMapping("/trusted-publish-policy")
    public PolicyResponse getPolicy(@PathVariable String slug, @RequestAttribute("userId") String actor) {
        // Read is authenticated; the policy is not secret. Member grants are exposed via the existing
        // namespace membership listing, which has its own authorization checks.
        return new PolicyResponse(namespaces.findBySlug(slug)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.slug.notFound", slug))
                .isTrustedPublishEnabled());
    }

    @PutMapping("/trusted-publish-policy")
    public PolicyResponse setPolicy(@PathVariable String slug, @Valid @RequestBody ToggleRequest request,
                                    @RequestAttribute("userId") String actor) {
        return new PolicyResponse(commands.setTrustedPublishEnabled(slug, request.enabled(), actor));
    }

    @PutMapping("/members/{memberId}/review-exemption")
    public MemberResponse setMemberExemption(@PathVariable String slug, @PathVariable String memberId,
                                             @Valid @RequestBody ToggleRequest request,
                                             @RequestAttribute("userId") String actor) {
        return commands.setReviewExempt(slug, memberId, request.enabled(), actor);
    }
}
