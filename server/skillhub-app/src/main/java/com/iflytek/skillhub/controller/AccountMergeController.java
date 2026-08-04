package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.MergeInitiateRequest;
import com.iflytek.skillhub.dto.MergeInitiateResponse;
import com.iflytek.skillhub.dto.MergeVerifyRequest;
import com.iflytek.skillhub.dto.MessageResponse;
import com.iflytek.skillhub.exception.UnauthorizedException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Compatibility endpoints for the temporarily isolated legacy account merge flow.
 *
 * <p>The previous implementation returned the secondary-account verification token to the
 * primary-account session and therefore did not prove independent control of both accounts. Keep
 * the routes stable for deployed clients, but fail closed until the safe account merge flow is
 * implemented.
 */
@RestController
@RequestMapping("/api/v1/account/merge")
public class AccountMergeController extends BaseApiController {

    public AccountMergeController(ApiResponseFactory responseFactory) {
        super(responseFactory);
    }

    @PostMapping("/initiate")
    public ApiResponse<MergeInitiateResponse> initiate(@AuthenticationPrincipal PlatformPrincipal principal,
                                                       @Valid @RequestBody MergeInitiateRequest request) {
        if (principal == null) {
            throw new UnauthorizedException("error.auth.required");
        }
        throw mergeTemporarilyUnavailable();
    }

    @PostMapping("/verify")
    public ApiResponse<MessageResponse> verify(@AuthenticationPrincipal PlatformPrincipal principal,
                                               @Valid @RequestBody MergeVerifyRequest request) {
        if (principal == null) {
            throw new UnauthorizedException("error.auth.required");
        }
        throw mergeTemporarilyUnavailable();
    }

    @PostMapping("/confirm")
    public ApiResponse<MessageResponse> confirm(@AuthenticationPrincipal PlatformPrincipal principal,
                                                @Valid @RequestBody ConfirmMergeRequest request) {
        if (principal == null) {
            throw new UnauthorizedException("error.auth.required");
        }
        throw mergeTemporarilyUnavailable();
    }

    public record ConfirmMergeRequest(@jakarta.validation.constraints.NotNull Long mergeRequestId) {}

    private AuthFlowException mergeTemporarilyUnavailable() {
        return new AuthFlowException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "error.auth.merge.temporarilyUnavailable"
        );
    }
}
