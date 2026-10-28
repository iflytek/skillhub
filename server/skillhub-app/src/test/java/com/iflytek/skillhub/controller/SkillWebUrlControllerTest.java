package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.controller.portal.SkillController;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.service.SkillLabelAppService;
import com.iflytek.skillhub.service.SkillSuiteAppService;
import com.iflytek.skillhub.service.SkillWebUrls;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SkillWebUrlControllerTest {
    @Test
    void detailResponseUsesTrustedConfigurationAndIgnoresForwardingHeaders() throws Exception {
        var query = mock(SkillQueryService.class);
        var detail = new SkillQueryService.SkillDetailDTO(
                1L, "demo", "Demo", "owner-1", "Alice", "Example",
                "PUBLIC", "ACTIVE", 0L, 0, 0, null, 0, false, 1L,
                java.time.Instant.EPOCH, java.time.Instant.EPOCH,
                false, false, true, true, null, null, null, null, "PUBLISHED", null, null);
        when(query.getSkillDetail(anyString(), anyString(), isNull(), anyMap(), anySet())).thenReturn(detail);
        var suites = mock(SkillSuiteAppService.class);
        when(suites.findVisibleMemberships(anyLong(), isNull(), anyMap(), anySet(), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(), 0, 0, 20));
        var factory = new ApiResponseFactory(new StaticMessageSource(), Clock.systemUTC(), mock(RequestIdAccessor.class));
        for (String base : new String[] {"https://public.example/prefix/", ""}) {
            var controller = new SkillController(query, null, mock(SkillLabelAppService.class), null,
                    suites, null, null, factory, new SkillWebUrls(base));
            var mvc = MockMvcBuilders.standaloneSetup(controller)
                    .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
            for (String path : List.of("/api/v1/skills/team/demo", "/api/web/skills/team/demo")) {
                mvc.perform(get(path).header("Host", "attacker.example")
                                .header("X-Forwarded-Host", "attacker.example")
                                .header("X-Forwarded-Proto", "https"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.webUrl").value(base.isEmpty()
                                ? org.hamcrest.Matchers.nullValue()
                                : org.hamcrest.Matchers.is("https://public.example/prefix/space/team/demo")));
            }
        }
    }
}
