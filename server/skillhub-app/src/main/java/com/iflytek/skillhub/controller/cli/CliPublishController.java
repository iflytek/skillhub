package com.iflytek.skillhub.controller.cli;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.controller.support.ZipPackageExtractor;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PublishResponse;
import com.iflytek.skillhub.ratelimit.RateLimit;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/cli")
public class CliPublishController extends BaseApiController {

    private final SkillPublishService skillPublishService;
    private final ZipPackageExtractor zipPackageExtractor;

    public CliPublishController(SkillPublishService skillPublishService,
                                ZipPackageExtractor zipPackageExtractor,
                                ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.skillPublishService = skillPublishService;
        this.zipPackageExtractor = zipPackageExtractor;
    }

    @PostMapping("/publish")
    @RateLimit(category = "publish", authenticated = 10, anonymous = 0)
    public ApiResponse<PublishResponse> publish(
            @RequestParam("file") MultipartFile file,
            @RequestParam("namespace") String namespace,
            @RequestParam("visibility") String visibility,
            @RequestAttribute("userId") String userId) throws IOException {

        SkillVisibility skillVisibility = SkillVisibility.valueOf(visibility.toUpperCase());

        List<PackageEntry> entries = zipPackageExtractor.extract(file);

        SkillPublishService.PublishResult publishResult = skillPublishService.publishFromEntries(
                namespace,
                entries,
                userId,
                skillVisibility
        );

        PublishResponse response = new PublishResponse(
                publishResult.skillId(),
                namespace,
                publishResult.slug(),
                publishResult.version().getVersion(),
                publishResult.version().getStatus().name(),
                publishResult.version().getFileCount(),
                publishResult.version().getTotalSize()
        );

        return ok("response.success.published", response);
    }
}
