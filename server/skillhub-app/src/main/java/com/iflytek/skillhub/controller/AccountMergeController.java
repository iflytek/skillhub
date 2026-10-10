package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.merge.AccountMergeService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.MergeInitiateRequest;
import com.iflytek.skillhub.dto.MergeInitiateResponse;
import com.iflytek.skillhub.dto.MergeVerifyRequest;
import com.iflytek.skillhub.dto.MergeApprovalDetailsResponse;
import com.iflytek.skillhub.dto.MessageResponse;
import com.iflytek.skillhub.exception.UnauthorizedException;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for initiating, verifying, and confirming account merge flows
 * across multiple identities owned by the same user.
 */
@RestController
@RequestMapping("/api/v1/account/merge")
public class AccountMergeController extends BaseApiController {

    private final AccountMergeService accountMergeService;

    public AccountMergeController(ApiResponseFactory responseFactory,
                                  AccountMergeService accountMergeService) {
        super(responseFactory);
        this.accountMergeService = accountMergeService;
    }

    @PostMapping("/initiate")
    public ApiResponse<MergeInitiateResponse> initiate(@AuthenticationPrincipal PlatformPrincipal principal,
                                                       @Valid @RequestBody MergeInitiateRequest request) {
        if (principal == null) {
            throw new UnauthorizedException("error.auth.required");
        }
        var result = accountMergeService.initiate(principal.userId(), request.secondaryIdentifier());
        return ok("response.success.created", new MergeInitiateResponse(
            result.mergeRequestId(),
            result.secondaryUserId(),
            result.expiresAt().toString()
        ));
    }

    @GetMapping("/requests/{mergeRequestId}")
    public ApiResponse<MergeApprovalDetailsResponse> approvalDetails(
        @AuthenticationPrincipal PlatformPrincipal principal,
        @PathVariable Long mergeRequestId
    ) {
        if (principal == null) {
            throw new UnauthorizedException("error.auth.required");
        }
        var details = accountMergeService.getApprovalDetails(principal.userId(), mergeRequestId);
        return ok("response.success.read", new MergeApprovalDetailsResponse(
            details.mergeRequestId(), details.primaryUserId(), details.primaryDisplayName(), details.expiresAt().toString()
        ));
    }

    @PostMapping("/verify")
    public ApiResponse<MessageResponse> verify(@AuthenticationPrincipal PlatformPrincipal principal,
                                               @Valid @RequestBody MergeVerifyRequest request) {
        if (principal == null) {
            throw new UnauthorizedException("error.auth.required");
        }
        accountMergeService.verify(
            principal.userId(),
            request.mergeRequestId()
        );
        return ok("response.success.updated", new MessageResponse("Account merge verified"));
    }

    @PostMapping("/confirm")
    public ApiResponse<MessageResponse> confirm(@AuthenticationPrincipal PlatformPrincipal principal,
                                                @Valid @RequestBody ConfirmMergeRequest request) {
        if (principal == null) {
            throw new UnauthorizedException("error.auth.required");
        }
        accountMergeService.confirm(principal.userId(), request.mergeRequestId());
        return ok("response.success.updated", new MessageResponse("Account merge completed"));
    }

    @PostMapping("/cancel")
    public ApiResponse<MessageResponse> cancel(@AuthenticationPrincipal PlatformPrincipal principal,
                                               @Valid @RequestBody CancelMergeRequest request) {
        if (principal == null) {
            throw new UnauthorizedException("error.auth.required");
        }
        accountMergeService.cancel(principal.userId(), request.mergeRequestId());
        return ok("response.success.updated", new MessageResponse("Account merge cancelled"));
    }

    public record ConfirmMergeRequest(@jakarta.validation.constraints.NotNull Long mergeRequestId) {}
    public record CancelMergeRequest(@jakarta.validation.constraints.NotNull Long mergeRequestId) {}
}
