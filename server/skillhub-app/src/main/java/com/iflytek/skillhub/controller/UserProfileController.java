package com.iflytek.skillhub.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import com.iflytek.skillhub.domain.user.ProfileChangeRequest;
import com.iflytek.skillhub.domain.user.ProfileChangeRequestRepository;
import com.iflytek.skillhub.domain.user.ProfileChangeStatus;
import com.iflytek.skillhub.domain.user.UpdateProfileResult;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserProfileService;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PendingChangesResponse;
import com.iflytek.skillhub.dto.ProfileUpdateStatus;
import com.iflytek.skillhub.dto.UpdateProfileRequest;
import com.iflytek.skillhub.dto.UpdateProfileResponse;
import com.iflytek.skillhub.dto.UserProfileResponse;
import com.iflytek.skillhub.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for user profile management.
 *
 * <p>Provides endpoints for viewing and updating the current user's profile.
 * All endpoints require authentication — users can only manage their own profile.
 */
@RestController
@RequestMapping("/api/v1/user/profile")
public class UserProfileController extends BaseApiController {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private final UserProfileService userProfileService;
    private final UserAccountRepository userAccountRepository;
    private final ProfileChangeRequestRepository changeRequestRepository;
    private final PlatformSessionService platformSessionService;

    public UserProfileController(ApiResponseFactory responseFactory,
                                  UserProfileService userProfileService,
                                  UserAccountRepository userAccountRepository,
                                  ProfileChangeRequestRepository changeRequestRepository,
                                  PlatformSessionService platformSessionService) {
        super(responseFactory);
        this.userProfileService = userProfileService;
        this.userAccountRepository = userAccountRepository;
        this.changeRequestRepository = changeRequestRepository;
        this.platformSessionService = platformSessionService;
    }

    /**
     * Get the current user's profile, including any pending change request.
     */
    @GetMapping
    public ApiResponse<UserProfileResponse> getProfile(
            @AuthenticationPrincipal PlatformPrincipal principal) {
        requireAuth(principal);

        UserAccount user = userAccountRepository.findById(principal.userId())
                .orElseThrow(() -> new UnauthorizedException("error.auth.required"));

        // Look up the most recent PENDING or REJECTED change request
        PendingChangesResponse pendingChanges = changeRequestRepository
                .findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
                        principal.userId(),
                        List.of(ProfileChangeStatus.PENDING, ProfileChangeStatus.REJECTED))
                .map(this::toPendingChangesResponse)
                .orElse(null);

        String displayName = user.getDisplayName();
        String avatarUrl = user.getAvatarUrl();
        if (pendingChanges != null && ProfileChangeStatus.PENDING.name().equals(pendingChanges.status())) {
            displayName = pendingChanges.changes().getOrDefault("displayName", displayName);
            avatarUrl = pendingChanges.changes().getOrDefault("avatarUrl", avatarUrl);
        }

        var response = new UserProfileResponse(
                displayName,
                avatarUrl,
                user.getEmail(),
                pendingChanges
        );
        return ok("response.success.read", response);
    }

    /**
     * Update the current user's profile fields.
     *
     * <p>Depending on moderation configuration, changes may be applied
     * immediately or queued for human review.
     */
    @PatchMapping
    public ApiResponse<UpdateProfileResponse> updateProfile(
            @AuthenticationPrincipal PlatformPrincipal principal,
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request,
            HttpServletRequest httpRequest) {
        requireAuth(principal);

        // Ensure at least one field is provided
        if (!request.hasChanges()) {
            throw new IllegalArgumentException("error.profile.noChanges");
        }

        // Trim displayName if present
        String displayName = request.displayName() != null
                ? request.displayName().trim()
                : null;

        // Build changes map from non-null fields
        Map<String, String> changes = new LinkedHashMap<>();
        if (displayName != null) {
            changes.put("displayName", displayName);
        }

        // Delegate to domain service
        UpdateProfileResult result = userProfileService.updateProfile(
                principal.userId(),
                changes,
                httpRequest.getHeader("X-Request-Id"),
                resolveClientIp(httpRequest),
                httpRequest.getHeader("User-Agent")
        );

        // Refresh session if changes were applied immediately
        var response = switch (result) {
            case UpdateProfileResult.Applied() -> {
                // Rebuild principal with updated displayName and refresh session
                refreshSession(principal, authentication, changes, httpRequest);
                yield new UpdateProfileResponse(
                        ProfileUpdateStatus.APPLIED,
                        "response.profile.updated"
                );
            }
            case UpdateProfileResult.PendingReview() -> new UpdateProfileResponse(
                    ProfileUpdateStatus.PENDING_REVIEW,
                    "response.profile.pendingReview"
            );
        };

        return ok("response.success.update", response);
    }

    /**
     * Refresh the session principal after profile changes are applied.
     * Mirrors the role-refresh pattern in AuthController.me().
     */
    private void refreshSession(PlatformPrincipal principal,
                                 Authentication authentication,
                                 Map<String, String> changes,
                                 HttpServletRequest request) {
        String newDisplayName = changes.getOrDefault("displayName", principal.displayName());
        String newAvatarUrl = changes.getOrDefault("avatarUrl", principal.avatarUrl());

        var updatedPrincipal = new PlatformPrincipal(
                principal.userId(),
                newDisplayName,
                principal.email(),
                newAvatarUrl,
                principal.oauthProvider(),
                principal.platformRoles()
        );
        platformSessionService.attachToAuthenticatedSession(
                updatedPrincipal, authentication, request, false);
    }

    /**
     * Convert a ProfileChangeRequest entity to the pending changes response DTO.
     */
    private PendingChangesResponse toPendingChangesResponse(ProfileChangeRequest request) {
        try {
            Map<String, String> changes = MAPPER.readValue(request.getChanges(), MAP_TYPE);
            return new PendingChangesResponse(
                    request.getStatus().name(),
                    changes,
                    request.getReviewComment(),
                    request.getCreatedAt()
            );
        } catch (Exception e) {
            // Malformed JSON in DB — return null rather than breaking the GET endpoint
            return null;
        }
    }

    /** Resolve client IP from proxy headers or direct connection. */
    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /** Guard — throw 401 if principal is missing. */
    private void requireAuth(PlatformPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("error.auth.required");
        }
    }
}
