package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.merge.AccountMergeService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.MergeInitiateRequest;
import com.iflytek.skillhub.dto.MergeInitiateResponse;
import com.iflytek.skillhub.dto.MergeVerifyRequest;
import com.iflytek.skillhub.dto.MessageResponse;
import com.iflytek.skillhub.exception.UnauthorizedException;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
            result.verificationToken(),
            result.expiresAt().toString()
        ));
    }

    @PostMapping("/verify")
    public ApiResponse<MessageResponse> verify(@AuthenticationPrincipal PlatformPrincipal principal,
                                               @Valid @RequestBody MergeVerifyRequest request) {
        if (principal == null) {
            throw new UnauthorizedException("error.auth.required");
        }
        accountMergeService.verifyAndComplete(
            principal.userId(),
            request.mergeRequestId(),
            request.verificationToken()
        );
        return ok("response.success.updated", new MessageResponse("Account merge completed"));
    }
}
