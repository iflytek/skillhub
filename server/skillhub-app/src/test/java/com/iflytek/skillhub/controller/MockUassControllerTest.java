package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.uass.MockUassLoginCoordinator;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.MockUassLoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MockUassControllerTest {

    private final MockUassLoginCoordinator coordinator = mock(MockUassLoginCoordinator.class);
    private final ApiResponseFactory responseFactory = mock(ApiResponseFactory.class);

    private final MockUassController controller = new MockUassController(responseFactory, coordinator);

    @Test
    void login_delegatesToCoordinator() {
        when(coordinator.submitLogin("state-1", "https://cb", "uss-1", "Alice", "13800138000", "alice@example.com"))
                .thenReturn("https://redirect");

        var request = new MockUassLoginRequest("state-1", "https://cb", "uss-1", "Alice", "13800138000", "alice@example.com");
        controller.login(request);
    }

    @Test
    void loginForm_returnsRedirect() {
        when(coordinator.submitLogin("state-1", "https://cb", "uss-1", "Alice", "13800138000", "alice@example.com"))
                .thenReturn("https://redirect");

        var request = new MockUassLoginRequest("state-1", "https://cb", "uss-1", "Alice", "13800138000", "alice@example.com");
        var response = controller.loginForm(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getFirst("Location")).isEqualTo("https://redirect");
    }
}
