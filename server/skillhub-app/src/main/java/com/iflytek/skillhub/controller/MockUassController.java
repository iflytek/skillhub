package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.uass.MockUassLoginCoordinator;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.MockUassLoginRequest;
import com.iflytek.skillhub.dto.MockUassLoginResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local-only HTTP boundary for simulating a third-party UASS login page.
 */
@RestController
@RequestMapping("/api/v1/auth/uass/mock")
@ConditionalOnProperty(prefix = "skillhub.auth.uass", name = "enabled", havingValue = "true")
public class MockUassController extends BaseApiController {

    private final MockUassLoginCoordinator mockUassLoginCoordinator;

    public MockUassController(ApiResponseFactory responseFactory,
                              MockUassLoginCoordinator mockUassLoginCoordinator) {
        super(responseFactory);
        this.mockUassLoginCoordinator = mockUassLoginCoordinator;
    }

    @PostMapping("/login")
    public ApiResponse<MockUassLoginResponse> login(@Valid @RequestBody MockUassLoginRequest request) {
        return ok(
                "response.success.read",
                new MockUassLoginResponse(mockUassLoginCoordinator.submitLogin(
                        request.state(),
                        request.callbackUrl(),
                        request.ussId(),
                        request.displayName(),
                        request.mobile(),
                        request.email()
                ))
        );
    }

    @PostMapping("/login-form")
    public ResponseEntity<Void> loginForm(@Valid @ModelAttribute MockUassLoginRequest request) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, mockUassLoginCoordinator.submitLogin(
                        request.state(),
                        request.callbackUrl(),
                        request.ussId(),
                        request.displayName(),
                        request.mobile(),
                        request.email()
                ))
                .build();
    }
}
