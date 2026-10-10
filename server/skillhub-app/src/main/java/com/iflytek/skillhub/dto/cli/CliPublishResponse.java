package com.iflytek.skillhub.dto.cli;

public record CliPublishResponse(
        String namespace,
        String slug,
        String version,
        String visibility
) {}
