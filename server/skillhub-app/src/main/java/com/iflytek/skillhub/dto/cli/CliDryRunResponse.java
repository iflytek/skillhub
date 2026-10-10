package com.iflytek.skillhub.dto.cli;

import java.util.List;

public record CliDryRunResponse(
        boolean valid,
        List<String> errors,
        List<String> warnings,
        String resolvedSlug,
        String resolvedVersion
) {}
