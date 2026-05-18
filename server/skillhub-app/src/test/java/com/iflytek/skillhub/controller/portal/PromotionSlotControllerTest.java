package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.domain.promotion.PromotionEventType;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.promotion.PromotionSlotItemResponse;
import com.iflytek.skillhub.service.promotion.PromotionCampaignAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.util.List;
import java.util.Locale;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone HTTP-boundary coverage for public promotion slot endpoints.
 */
class PromotionSlotControllerTest {

    private MockMvc mockMvc;
    private PromotionCampaignAppService appService;

    @BeforeEach
    void setUp() {
        appService = mock(PromotionCampaignAppService.class);
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("response.success.created", Locale.ROOT, "created");
        messageSource.addMessage("response.success.read", Locale.ROOT, "ok");
        ApiResponseFactory factory = new ApiResponseFactory(messageSource, Clock.systemUTC());
        mockMvc = MockMvcBuilders.standaloneSetup(new PromotionSlotController(appService, factory)).build();
    }

    @Test
    void listSlotItems_returnsPublicSlotItems() throws Exception {
        given(appService.listSlotItems("HOME_HERO")).willReturn(List.of(
                new PromotionSlotItemResponse(7L, "HOME_HERO", null, 1L,
                        "Featured", "subtitle", null, null, "/space/global/demo")
        ));

        mockMvc.perform(get("/api/v1/promotion-slots/HOME_HERO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].campaignId").value(7));
    }

    @Test
    void recordEvent_usesPublicEndpointAndAllowsAnonymousIdentity() throws Exception {
        mockMvc.perform(post("/api/v1/promotion-slots/campaigns/7/events/CLICK")
                        .header("X-Anonymous-Id", "anon-1")
                        .header("X-Request-Id", "req-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(appService).recordEvent(eq(7L), eq(PromotionEventType.CLICK), eq(null), eq("anon-1"), eq("req-1"));
    }
}
