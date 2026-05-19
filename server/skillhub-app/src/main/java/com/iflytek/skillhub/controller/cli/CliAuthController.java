package com.iflytek.skillhub.controller.cli;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.cli.CliWhoAmIResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cli/v1/auth")
public class CliAuthController extends BaseApiController {
    public CliAuthController(ApiResponseFactory responseFactory) {
        super(responseFactory);
    }

    @GetMapping("/whoami")
    public ApiResponse<CliWhoAmIResponse> whoami(@AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.read", new CliWhoAmIResponse(
                principal.userId(),
                principal.displayName(),
                principal.email()
        ));
    }
}
