package com.iflytek.skillhub.controller.cli;

import com.iflytek.skillhub.controller.BaseApiController;
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
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RestController
@RequestMapping("/api/v1/cli")
public class CliPublishController extends BaseApiController {

    private final SkillPublishService skillPublishService;

    public CliPublishController(SkillPublishService skillPublishService,
                                ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.skillPublishService = skillPublishService;
    }

    @PostMapping("/publish")
    @RateLimit(category = "publish", authenticated = 10, anonymous = 0)
    public ApiResponse<PublishResponse> publish(
            @RequestParam("file") MultipartFile file,
            @RequestParam("namespace") String namespace,
            @RequestParam("visibility") String visibility,
            @RequestAttribute("userId") String userId) throws IOException {

        SkillVisibility skillVisibility = SkillVisibility.valueOf(visibility.toUpperCase());

        List<PackageEntry> entries = extractZipEntries(file);

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

    private List<PackageEntry> extractZipEntries(MultipartFile file) throws IOException {
        List<PackageEntry> entries = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                if (!zipEntry.isDirectory()) {
                    byte[] content = zis.readAllBytes();
                    entries.add(new PackageEntry(
                            zipEntry.getName(),
                            content,
                            content.length,
                            determineContentType(zipEntry.getName())
                    ));
                }
                zis.closeEntry();
            }
        }

        return entries;
    }

    private String determineContentType(String filename) {
        if (filename.endsWith(".py")) return "text/x-python";
        if (filename.endsWith(".json")) return "application/json";
        if (filename.endsWith(".yaml") || filename.endsWith(".yml")) return "application/x-yaml";
        if (filename.endsWith(".txt")) return "text/plain";
        if (filename.endsWith(".md")) return "text/markdown";
        return "application/octet-stream";
    }
}
