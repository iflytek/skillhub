package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.merge.AccountMergeService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.MergeInitiateRequest;
import com.iflytek.skillhub.dto.MergeVerifyRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountMergeControllerCoverageTest {

    private final AccountMergeService accountMergeService = mock(AccountMergeService.class);
    private final ApiResponseFactory responseFactory = mock(ApiResponseFactory.class);

    private final AccountMergeController controller = new AccountMergeController(responseFactory, accountMergeService);

    @Test
    void initiate_withNullPrincipal_throwsUnauthorized() {
        assertThatThrownBy(() -> controller.initiate(null, new MergeInitiateRequest("sec")))
                .isInstanceOf(com.iflytek.skillhub.exception.UnauthorizedException.class);
    }

    @Test
    void verify_withNullPrincipal_throwsUnauthorized() {
        assertThatThrownBy(() -> controller.verify(null, new MergeVerifyRequest(1L, "token")))
                .isInstanceOf(com.iflytek.skillhub.exception.UnauthorizedException.class);
    }

    @Test
    void confirm_withNullPrincipal_throwsUnauthorized() {
        assertThatThrownBy(() -> controller.confirm(null, new AccountMergeController.ConfirmMergeRequest(1L)))
                .isInstanceOf(com.iflytek.skillhub.exception.UnauthorizedException.class);
    }

}
