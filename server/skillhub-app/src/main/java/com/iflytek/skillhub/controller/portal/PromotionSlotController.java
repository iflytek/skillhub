package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.promotion.PromotionSlotItemResponse;
import com.iflytek.skillhub.service.promotion.PromotionCampaignAppService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Anonymous-readable promotion slot lookup. Returns only ACTIVE campaigns whose
 * windows are currently open.
 */
@RestController
@RequestMapping("/api/v1/promotion-slots")
public class PromotionSlotController extends BaseApiController {

    private final PromotionCampaignAppService promotionCampaignAppService;

    public PromotionSlotController(PromotionCampaignAppService promotionCampaignAppService,
                                   ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.promotionCampaignAppService = promotionCampaignAppService;
    }

    @GetMapping("/{slotCode}")
    public ApiResponse<List<PromotionSlotItemResponse>> listSlotItems(@PathVariable String slotCode) {
        return ok("response.success.read", promotionCampaignAppService.listSlotItems(slotCode));
    }
}
