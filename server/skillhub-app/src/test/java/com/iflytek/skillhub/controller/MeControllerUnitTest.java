package com.iflytek.skillhub.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.controller.portal.MeController;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.exception.UnauthorizedException;
import com.iflytek.skillhub.service.MySkillAppService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticMessageSource;

@ExtendWith(MockitoExtension.class)
class MeControllerUnitTest {

    @Mock
    private MySkillAppService mySkillAppService;

    private MeController controller;

    @BeforeEach
    void setUp() {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("response.success.read", java.util.Locale.getDefault(), "ok");
        ApiResponseFactory responseFactory = new ApiResponseFactory(
                messageSource,
                Clock.fixed(Instant.parse("2026-03-20T00:00:00Z"), ZoneOffset.UTC)
        );
        controller = new MeController(mySkillAppService, responseFactory);
    }

    @Test
    void listMySkills_shouldThrowWhenPrincipalNull() {
        assertThatThrownBy(() -> controller.listMySkills(0, 10, null, null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("error.auth.required");
    }

    @Test
    void listMyStars_shouldThrowWhenPrincipalNull() {
        assertThatThrownBy(() -> controller.listMyStars(0, 12, null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("error.auth.required");
    }

    @Test
    void listMySkills_shouldSucceedWithValidPrincipal() {
        com.iflytek.skillhub.auth.rbac.PlatformPrincipal principal =
                new com.iflytek.skillhub.auth.rbac.PlatformPrincipal(
                        "user-42", "tester", "tester@example.com", "", "github", Set.of("USER")
                );
        when(mySkillAppService.listMySkills("user-42", 0, 10, null, Set.of("USER")))
                .thenReturn(new PageResponse<>(List.of(), 0, 0, 10));

        var response = controller.listMySkills(0, 10, null, principal);

        org.assertj.core.api.Assertions.assertThat(response.code()).isEqualTo(0);
    }

    @Test
    void listMyStars_shouldSucceedWithValidPrincipal() {
        com.iflytek.skillhub.auth.rbac.PlatformPrincipal principal =
                new com.iflytek.skillhub.auth.rbac.PlatformPrincipal(
                        "user-42", "tester", "tester@example.com", "", "github", Set.of("USER")
                );
        when(mySkillAppService.listMyStars("user-42", 0, 12))
                .thenReturn(new PageResponse<>(List.of(), 0, 0, 12));

        var response = controller.listMyStars(0, 12, principal);

        org.assertj.core.api.Assertions.assertThat(response.code()).isEqualTo(0);
    }
}
