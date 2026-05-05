package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.auth.local.PasswordResetService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.service.AdminUserAppService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class UserManagementControllerDirectTest {

    private final AdminUserAppService adminUserAppService = mock(AdminUserAppService.class);
    private final PasswordResetService passwordResetService = mock(PasswordResetService.class);
    private final ApiResponseFactory responseFactory = mock(ApiResponseFactory.class);

    private final UserManagementController controller = new UserManagementController(
            adminUserAppService, passwordResetService, responseFactory
    );

    @Test
    void triggerPasswordReset_withNullPrincipal_throwsUnauthorized() {
        assertThatThrownBy(() -> controller.triggerPasswordReset("user-123", null))
                .isInstanceOf(com.iflytek.skillhub.exception.UnauthorizedException.class);
    }
}
