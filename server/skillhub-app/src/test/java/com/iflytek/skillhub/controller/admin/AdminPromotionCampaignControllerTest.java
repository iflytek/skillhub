package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.domain.promotion.PromotionCampaign;
import com.iflytek.skillhub.domain.promotion.PromotionCampaignStatus;
import com.iflytek.skillhub.domain.promotion.PromotionTargetType;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.promotion.PromotionCampaignResponse;
import com.iflytek.skillhub.service.promotion.PromotionCampaignAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc tests for {@link AdminPromotionCampaignController}. Pure HTTP boundary
 * checks — domain authorization is exercised in {@code PromotionCampaignServiceTest}.
 */
class AdminPromotionCampaignControllerTest {

    private MockMvc mockMvc;
    private PromotionCampaignAppService appService;

    @BeforeEach
    void setUp() {
        appService = mock(PromotionCampaignAppService.class);
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("response.success.created", Locale.ROOT, "created");
        messageSource.addMessage("response.success.updated", Locale.ROOT, "updated");
        messageSource.addMessage("response.success.read",   Locale.ROOT, "ok");
        ApiResponseFactory factory = new ApiResponseFactory(messageSource, Clock.systemUTC());

        AdminPromotionCampaignController controller = new AdminPromotionCampaignController(appService, factory);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void listByStatus_returnsPagedResponse() throws Exception {
        PromotionCampaignResponse resp = sampleResponse(1L);
        given(appService.listByStatus(eq(PromotionCampaignStatus.PENDING_REVIEW), anyInt(), anyInt()))
                .willReturn(new PageResponse<>(List.of(resp), 1, 0, 20));

        mockMvc.perform(get("/api/v1/admin/promotion-campaigns")
                        .requestAttr("userId", "admin-1")
                        .param("status", "PENDING_REVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void create_passesPayloadToAppServiceAndReturnsCreated() throws Exception {
        given(appService.createCampaign(any(), eq("admin-1")))
                .willReturn(sampleResponse(1L));

        String body = """
            {
              "targetType": "SKILL_BUNDLE",
              "targetId": 88,
              "targetVersionId": 120,
              "slotCode": "HOME_HERO",
              "title": "Featured",
              "priority": 80,
              "startsAt": "2026-06-01T00:00:00Z",
              "endsAt": "2026-06-30T23:59:59Z"
            }
            """;

        mockMvc.perform(post("/api/v1/admin/promotion-campaigns")
                        .requestAttr("userId", "admin-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void approve_passesIdAndCommentToAppService() throws Exception {
        given(appService.approve(eq(1L), anyString(), eq("admin-1")))
                .willReturn(sampleResponse(1L));

        mockMvc.perform(post("/api/v1/admin/promotion-campaigns/1/approve")
                        .requestAttr("userId", "admin-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void reject_acceptsEmptyBody() throws Exception {
        given(appService.reject(eq(1L), eq(null), eq("admin-1")))
                .willReturn(sampleResponse(1L));

        mockMvc.perform(post("/api/v1/admin/promotion-campaigns/1/reject")
                        .requestAttr("userId", "admin-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private PromotionCampaign sampleCampaign(Long id) {
        PromotionCampaign campaign = new PromotionCampaign(
                PromotionTargetType.SKILL_BUNDLE, 88L, "HOME_HERO",
                "Title", 80, Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-30T23:59:59Z"),
                "alice");
        campaign.setStatus(PromotionCampaignStatus.PENDING_REVIEW);
        try {
            java.lang.reflect.Field f = PromotionCampaign.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(campaign, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return campaign;
    }

    private PromotionCampaignResponse sampleResponse(Long id) {
        return PromotionCampaignResponse.from(sampleCampaign(id));
    }
}
