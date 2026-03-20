package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.SkillLabelDto;
import com.iflytek.skillhub.service.PublicLabelAppService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/labels", "/api/web/labels"})
public class LabelController extends BaseApiController {

    private final PublicLabelAppService publicLabelAppService;

    public LabelController(PublicLabelAppService publicLabelAppService,
                           ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.publicLabelAppService = publicLabelAppService;
    }

    @GetMapping
    public ApiResponse<List<SkillLabelDto>> listVisibleLabels() {
        return ok("response.success.read", publicLabelAppService.listVisibleFilters());
    }
}
