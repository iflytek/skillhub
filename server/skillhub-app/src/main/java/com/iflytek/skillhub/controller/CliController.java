package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.controller.support.SkillPackageArchiveExtractor;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator;
import com.iflytek.skillhub.domain.skill.validation.ValidationResult;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.CliWhoamiResponse;
import com.iflytek.skillhub.dto.SkillCheckResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.iflytek.skillhub.exception.UnauthorizedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/cli")
public class CliController extends BaseApiController {

    private final SkillPackageValidator skillPackageValidator;
    private final SkillPackageArchiveExtractor skillPackageArchiveExtractor;

    public CliController(ApiResponseFactory responseFactory,
                         SkillPackageValidator skillPackageValidator,
                         SkillPackageArchiveExtractor skillPackageArchiveExtractor) {
        super(responseFactory);
        this.skillPackageValidator = skillPackageValidator;
        this.skillPackageArchiveExtractor = skillPackageArchiveExtractor;
    }

    @GetMapping("/whoami")
    public ApiResponse<CliWhoamiResponse> whoami(@AuthenticationPrincipal PlatformPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("error.auth.required");
        }

        return ok("response.success.read", CliWhoamiResponse.from(principal));
    }

    @PostMapping("/check")
    public ApiResponse<SkillCheckResponse> check(@RequestParam("file") MultipartFile file) throws IOException {
        List<PackageEntry> entries;
        try {
            entries = skillPackageArchiveExtractor.extract(file);
        } catch (IllegalArgumentException e) {
            SkillCheckResponse response = new SkillCheckResponse(
                    false,
                    List.of(e.getMessage()),
                    0,
                    0L
            );
            return ok("response.success.validated", response);
        }
        ValidationResult result = skillPackageValidator.validate(entries);

        SkillCheckResponse response = new SkillCheckResponse(
                result.passed(),
                result.errors(),
                entries.size(),
                entries.stream().mapToLong(PackageEntry::size).sum()
        );

        return ok("response.success.validated", response);
    }
}
